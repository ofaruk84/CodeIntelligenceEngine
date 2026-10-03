package com.codeintel.adapter.out.javaparser;

import com.codeintel.application.port.SourceAnalyzer;
import com.codeintel.application.result.RepositorySources;
import com.codeintel.domain.model.*;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Local two-pass adapter; private AST state never crosses the application port. */
public final class JavaParserSourceAnalyzer implements SourceAnalyzer {
    @Override public List<SourceUnit> analyze(RepositorySources sources) {
        var units = new ArrayList<SourceUnit>();
        var asts = new java.util.LinkedHashMap<String, com.github.javaparser.ast.CompilationUnit>();
        for (var file : sources.files()) {
            String path = file.toString().replace('\\', '/');
            try {
                units.add(parse(path, Files.readString(sources.repository().resolve(file), StandardCharsets.UTF_8), ast -> {
                    ast.setStorage(sources.repository().resolve(file));
                    asts.put(path, ast);
                }));
            } catch (IOException | SecurityException failure) {
                units.add(failed(path, List.of(new AnalysisDiagnostic(DiagnosticSeverity.ERROR,
                        "SOURCE_READ_ERROR", failure.getClass().getSimpleName() + ": " + failure.getMessage(),
                        Optional.empty(), List.of()))));
            }
        }
        var configuration = SymbolResolverConfiguration.create(sources, units);
        var resolver = new CallResolver(sources.repository(), units);
        return units.stream().map(unit -> {
            var ast = asts.get(unit.path());
            if (ast == null || unit.partial()) return unit;
            ast.setData(com.github.javaparser.ast.Node.SYMBOL_RESOLVER_KEY, configuration.getSymbolResolver().orElseThrow());
            return resolver.resolve(unit, ast);
        }).toList();
    }

    /** Also accepts in-memory source for embedding and synthetic tests. */
    public SourceUnit parse(String path, String source) {
        return parse(path, source, ast -> {});
    }

    private SourceUnit parse(String path, String source, java.util.function.Consumer<com.github.javaparser.ast.CompilationUnit> retain) {
        var parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21).setTabSize(1));
        var result = parser.parse(source);
        var diagnostics = result.getProblems().stream().map(problem -> {
            var location = problem.getLocation().flatMap(tokens -> tokens.toRange())
                    .map(range -> new SourceLocation(path, range.begin.line, range.begin.column,
                            range.end.line, range.end.column));
            return new AnalysisDiagnostic(DiagnosticSeverity.ERROR, "PARSE_ERROR",
                    problem.getVerboseMessage(), location, List.of());
        }).toList();
        // Recovery nodes may span missing or synthesized syntax. Never promote them to definitions.
        if (!result.isSuccessful()) return failed(path, diagnostics);
        try {
            var ast = result.getResult().orElseThrow();
            var unit = new AstExtractor(path, source).extract(ast);
            retain.accept(ast);
            return unit;
        } catch (AstExtractor.InvalidDeclaration invalidDeclaration) {
            // Parser-successful syntax can still violate declaration invariants (for example varargs order).
            return failed(path, List.of(new AnalysisDiagnostic(DiagnosticSeverity.ERROR, "INVALID_DECLARATION",
                    invalidDeclaration.getMessage(), Optional.of(invalidDeclaration.location()), List.of())));
        }
    }

    private SourceUnit failed(String path, List<AnalysisDiagnostic> diagnostics) {
        return new SourceUnit(path, "", List.of(), List.of(), List.of(), diagnostics, true);
    }
}
