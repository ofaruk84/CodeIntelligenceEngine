package com.codeintel.adapter.out.javaparser;

import com.codeintel.domain.model.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.*;
import java.nio.file.Path;
import java.util.*;

/** Resolves each occurrence independently and maps source declarations by their exact range. */
final class CallResolver {
    private final Path repository;
    private final Map<SourceLocation, MethodId> definitions = new HashMap<>();

    CallResolver(Path repository, List<SourceUnit> units) {
        this.repository = repository;
        units.forEach(u -> u.types().forEach(t -> t.methods().forEach(m -> definitions.put(m.location(), m.id()))));
    }

    SourceUnit resolve(SourceUnit unit, CompilationUnit ast) {
        var nodes = new HashMap<SourceLocation, Node>();
        ast.walk(n -> {
            if (n instanceof MethodCallExpr || n instanceof ObjectCreationExpr || n instanceof ExplicitConstructorInvocationStmt)
                nodes.put(location(unit.path(), n), n);
        });
        var diagnostics = new ArrayList<>(unit.diagnostics());
        var calls = new ArrayList<MethodCall>();
        for (var call : unit.calls()) {
            try {
                Node node = nodes.get(call.location());
                ResolvedMethodLikeDeclaration declaration;
                if (node instanceof MethodCallExpr method) declaration = method.resolve();
                else if (node instanceof ObjectCreationExpr creation) declaration = creation.resolve();
                else declaration = ((ExplicitConstructorInvocationStmt) node).resolve();
                calls.add(new MethodCall(call.rawExpression(), call.name(), call.scope(), call.location(), call.caller(),
                        Optional.of(identity(declaration)), ResolutionStatus.RESOLVED, Optional.empty()));
            } catch (UnsolvedSymbolException | UnsupportedOperationException | IllegalStateException | IllegalArgumentException
                     | com.github.javaparser.resolution.MethodAmbiguityException
                     | com.github.javaparser.resolution.logic.ConflictingGenericTypesException
                     | com.github.javaparser.ParseProblemException failure) {
                String category = failure instanceof UnsolvedSymbolException ? "UNSOLVED_SYMBOL" : "SOLVER_LIMITATION";
                String message = failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "No solver detail");
                calls.add(new MethodCall(call.rawExpression(), call.name(), call.scope(), call.location(), call.caller(),
                        Optional.empty(), ResolutionStatus.UNRESOLVED, Optional.of(new ResolutionFailure(category, message, List.of()))));
                diagnostics.add(new AnalysisDiagnostic(DiagnosticSeverity.WARNING, category, message, Optional.of(call.location()), List.of()));
            }
        }
        return new SourceUnit(unit.path(), unit.packageName(), unit.imports(), unit.types(), calls, diagnostics, unit.partial());
    }

    private MethodId identity(ResolvedMethodLikeDeclaration declaration) {
        var ast = declaration.toAst();
        if (ast.isPresent()) {
            var node = ast.orElseThrow();
            var storage = node.findCompilationUnit().flatMap(CompilationUnit::getStorage);
            if (storage.isPresent() && node.getRange().isPresent()) {
                Path absolute = storage.orElseThrow().getPath().toAbsolutePath().normalize();
                if (absolute.startsWith(repository)) {
                    var id = definitions.get(location(repository.relativize(absolute).toString().replace('\\', '/'), node));
                    if (id != null) return id;
                    // Implicit constructors/accessors have no collected definition; retain an external target.
                }
            }
        }
        var owner = declaration.declaringType();
        String qualified = owner.getPackageName().isEmpty() ? owner.getClassName().replace('.', '$')
                : owner.getPackageName() + "." + owner.getClassName().replace('.', '$');
        var parameters = new ArrayList<TypeReference>();
        for (int i = 0; i < declaration.getNumberOfParams(); i++) parameters.add(ResolvedTypeIdentity.type(declaration.getParam(i).getType()));
        return MethodId.canonical(SymbolId.canonical(qualified), declaration instanceof ResolvedConstructorDeclaration ? "<init>" : declaration.getName(), parameters);
    }

    private static SourceLocation location(String path, Node node) {
        var range = node.getRange().orElseThrow();
        return new SourceLocation(path, range.begin.line, range.begin.column, range.end.line, range.end.column);
    }
}
