package com.codeintel.adapter.out.javaparser;

import com.codeintel.domain.model.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.Type;
import java.util.*;
import java.util.stream.Collectors;

/** AST state is scoped to one extraction and never escapes this adapter. */
final class AstExtractor {
    private final String path;
    private final String source;
    private final List<Integer> lineOffsets = new ArrayList<>();
    private final Map<Node, SymbolId> owners = new IdentityHashMap<>();
    private final Map<Node, MethodId> callables = new IdentityHashMap<>();
    private final List<ClassNode> types = new ArrayList<>();
    private final List<MethodCall> calls = new ArrayList<>();
    private String packageName;

    AstExtractor(String path, String source) {
        this.path = path;
        this.source = source;
        lineOffsets.add(0);
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '\r') {
                if (index + 1 < source.length() && source.charAt(index + 1) == '\n') index++;
                lineOffsets.add(index + 1);
            } else if (character == '\n') lineOffsets.add(index + 1);
        }
    }

    SourceUnit extract(CompilationUnit unit) {
        packageName = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        collectTypes(unit);
        types.sort(Comparator.comparingInt((ClassNode c) -> c.location().startLine())
                .thenComparingInt(c -> c.location().startColumn()));
        unit.walk(node -> {
            if (node instanceof MethodCallExpr call)
                addCall(call, call.getNameAsString(), call.getScope().map(this::raw));
            else if (node instanceof ObjectCreationExpr call)
                addCall(call, call.getType().getNameAsString(), call.getScope().map(this::raw));
            else if (node instanceof ExplicitConstructorInvocationStmt call)
                addCall(call, call.isThis() ? "this" : "super", call.getExpression().map(this::raw));
        });
        calls.sort(Comparator.comparingInt((MethodCall c) -> c.location().startLine())
                .thenComparingInt(c -> c.location().startColumn()));
        return new SourceUnit(path, packageName, unit.getImports().stream().map(i ->
                new ImportInfo(i.getNameAsString(), i.isStatic(), i.isAsterisk(), location(i))).toList(),
                types, calls, List.of(), false);
    }

    private void collectTypes(Node node) {
        try {
            if (node instanceof TypeDeclaration<?> declaration) collectType(node, declaration, declaration.getMembers());
            else if (node instanceof ObjectCreationExpr creation && creation.getAnonymousClassBody().isPresent())
                collectType(node, null, creation.getAnonymousClassBody().orElseThrow());
            else if (node instanceof EnumConstantDeclaration constant && !constant.getClassBody().isEmpty())
                collectType(node, null, constant.getClassBody());
        } catch (IllegalArgumentException invalidDeclaration) {
            throw new InvalidDeclaration(location(node), invalidDeclaration);
        }
        for (Node child : node.getChildNodes()) collectTypes(child);
    }

    private void collectType(Node node, TypeDeclaration<?> declaration, NodeList<BodyDeclaration<?>> members) {
        Optional<SymbolId> enclosing = enclosing(node);
        boolean anonymous = declaration == null;
        boolean member = node.getParentNode().filter(p -> p instanceof TypeDeclaration<?>
                || p instanceof ObjectCreationExpr || p instanceof EnumConstantDeclaration).isPresent();
        var nesting = anonymous ? ClassNode.Nesting.ANONYMOUS : enclosing.isEmpty()
                ? ClassNode.Nesting.TOP_LEVEL : member ? ClassNode.Nesting.MEMBER : ClassNode.Nesting.LOCAL;
        String name = anonymous ? "Anonymous" : declaration.getNameAsString();
        String qualified = enclosing.map(id -> id.qualifiedName() + "$" + name)
                .orElse(packageName.isEmpty() ? name : packageName + "." + name);
        var id = SymbolId.canonical(qualified);
        if (nesting == ClassNode.Nesting.LOCAL || anonymous || enclosing.filter(e -> e.declaration().isPresent()).isPresent())
            id = id.at(location(node));
        owners.put(node, id);
        var fields = new ArrayList<FieldInfo>();
        var methods = new ArrayList<MethodNode>();
        if (declaration instanceof RecordDeclaration record)
            for (var component : record.getParameters()) {
                var parameter = parameter(component);
                fields.add(new FieldInfo(parameter.name(), parameter.type(), Set.of("private", "final"), parameter.location()));
            }
        for (var body : members) {
            if (body instanceof FieldDeclaration field)
                for (var variable : field.getVariables()) fields.add(new FieldInfo(variable.getNameAsString(),
                        type(variable.getType()), modifiers(field.getModifiers()), location(variable)));
            if (body instanceof CallableDeclaration<?> callable) {
                var parameters = callable.getParameters().stream().map(this::parameter).toList();
                boolean constructor = callable instanceof ConstructorDeclaration;
                var method = method(id, callable, constructor ? "<init>" : callable.getNameAsString(),
                        constructor ? Optional.empty() : Optional.of(type(((MethodDeclaration) callable).getType())),
                        parameters, modifiers(callable.getModifiers()), callable.getThrownExceptions().stream().map(this::type).toList());
                methods.add(method);
            } else if (body instanceof CompactConstructorDeclaration compact && declaration instanceof RecordDeclaration record) {
                methods.add(method(id, compact, "<init>", Optional.empty(), record.getParameters().stream()
                        .map(this::parameter).toList(), modifiers(compact.getModifiers()), List.of()));
            } else if (body instanceof AnnotationMemberDeclaration annotation) {
                methods.add(method(id, annotation, annotation.getNameAsString(), Optional.of(type(annotation.getType())),
                        List.of(), modifiers(annotation.getModifiers()), List.of()));
            }
        }
        var supers = new ArrayList<TypeReference>();
        if (declaration instanceof ClassOrInterfaceDeclaration c) {
            c.getExtendedTypes().forEach(t -> supers.add(type(t)));
            c.getImplementedTypes().forEach(t -> supers.add(type(t)));
        } else if (declaration instanceof EnumDeclaration e) e.getImplementedTypes().forEach(t -> supers.add(type(t)));
        else if (declaration instanceof RecordDeclaration r) r.getImplementedTypes().forEach(t -> supers.add(type(t)));
        TypeKind kind = declaration instanceof ClassOrInterfaceDeclaration c && c.isInterface() ? TypeKind.INTERFACE
                : declaration instanceof EnumDeclaration ? TypeKind.ENUM : declaration instanceof RecordDeclaration ? TypeKind.RECORD
                : declaration instanceof AnnotationDeclaration ? TypeKind.ANNOTATION : TypeKind.CLASS;
        types.add(new ClassNode(id, anonymous ? Optional.empty() : Optional.of(name), kind, nesting, enclosing,
                anonymous ? Set.of() : modifiers(declaration.getModifiers()), supers, fields, methods, location(node)));
    }

    private MethodNode method(SymbolId owner, Node node, String name, Optional<TypeReference> returns,
                              List<ParameterInfo> parameters, Set<String> modifiers, List<TypeReference> thrown) {
        var parameterTypes = parameters.stream().map(ParameterInfo::type).toList();
        var id = new MethodId(owner, name, parameterTypes, parameterTypes.stream().anyMatch(t -> !t.resolved())
                ? Optional.of(location(node)) : Optional.empty());
        callables.put(node, id);
        return new MethodNode(id, name.equals("<init>") ? CallableKind.CONSTRUCTOR : CallableKind.METHOD,
                returns, parameters, modifiers, thrown, location(node));
    }

    private ParameterInfo parameter(Parameter p) {
        var reference = type(p.getType());
        return new ParameterInfo(p.getNameAsString(), p.isVarArgs() ? reference.asArray() : reference,
                p.isVarArgs(), modifiers(p.getModifiers()), location(p));
    }

    private TypeReference type(Type original) {
        Type base = original.clone();
        int dimensions = 0;
        while (base.isArrayType()) { dimensions++; base = base.asArrayType().getComponentType(); }
        base.walk(n -> { n.removeComment(); if (n instanceof com.github.javaparser.ast.nodeTypes.NodeWithAnnotations<?> a)
            a.getAnnotations().clear(); });
        boolean resolved = base.isPrimitiveType() || base.isVoidType();
        return new TypeReference(base.toString(), dimensions, resolved);
    }

    private Optional<SymbolId> enclosing(Node node) {
        for (Node parent = node.getParentNode().orElse(null); parent != null; parent = parent.getParentNode().orElse(null))
            if (owners.containsKey(parent)) return Optional.of(owners.get(parent));
        return Optional.empty();
    }

    private void addCall(Node node, String name, Optional<String> scope) {
        Optional<MethodId> caller = Optional.empty();
        Node child = node;
        for (Node parent = node.getParentNode().orElse(null); parent != null; child = parent, parent = parent.getParentNode().orElse(null)) {
            if (callables.containsKey(parent)) { caller = Optional.of(callables.get(parent)); break; }
            if (owners.containsKey(parent)) {
                // Constructor arguments/scopes execute in the surrounding context, outside the anonymous body.
                if (parent instanceof ObjectCreationExpr creation
                        && !creation.getAnonymousClassBody().orElseThrow().contains(child)) continue;
                if (parent instanceof EnumConstantDeclaration constant && !constant.getClassBody().contains(child)) continue;
                break;
            }
        }
        calls.add(new MethodCall(raw(node), name, scope, location(node), caller, Optional.empty(),
                ResolutionStatus.UNRESOLVED, Optional.of(new ResolutionFailure("NOT_ATTEMPTED",
                "Call resolution is deferred to the second pass", List.of()))));
    }

    private Set<String> modifiers(NodeList<Modifier> modifiers) {
        return modifiers.stream().map(m -> m.getKeyword().asString()).collect(Collectors.toSet());
    }

    private SourceLocation location(Node node) {
        var range = node.getRange().orElseThrow();
        return new SourceLocation(path, range.begin.line, range.begin.column, range.end.line, range.end.column);
    }

    private String raw(Node node) {
        var range = node.getRange().orElseThrow();
        int start = offset(range.begin.line, range.begin.column);
        int end = offset(range.end.line, range.end.column);
        return source.substring(start, end + 1);
    }

    private int offset(int line, int column) {
        return lineOffsets.get(line - 1) + column - 1;
    }
    /** Only declaration-to-domain validation failures are expected extraction failures. */
    static final class InvalidDeclaration extends IllegalArgumentException {
        private final SourceLocation location;

        InvalidDeclaration(SourceLocation location, IllegalArgumentException cause) {
            super(cause.getMessage(), cause);
            this.location = location;
        }

        SourceLocation location() { return location; }
    }

}
