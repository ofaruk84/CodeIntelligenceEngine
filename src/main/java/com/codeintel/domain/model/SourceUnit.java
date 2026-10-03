package com.codeintel.domain.model;

import java.util.List;
import java.util.Objects;

/** Extracted source data. Types are flat, including nested types; call sites occur exactly once in this list. */
public record SourceUnit(String path, String packageName, List<ImportInfo> imports,
                         List<ClassNode> types, List<MethodCall> calls,
                         List<AnalysisDiagnostic> diagnostics, boolean partial) {
    public SourceUnit {
        path = ModelChecks.relativePath(path);
        Objects.requireNonNull(packageName, "packageName");
        if (!packageName.isEmpty()) ModelChecks.name(packageName);
        imports = List.copyOf(imports);
        types = List.copyOf(types);
        calls = List.copyOf(calls);
        diagnostics = List.copyOf(diagnostics);
        for (ImportInfo value : imports) requirePath(path, value.location());
        for (ClassNode type : types) {
            requirePath(path, type.location());
            for (FieldInfo field : type.fields()) requirePath(path, field.location());
            for (MethodNode method : type.methods()) {
                requirePath(path, method.location());
                for (ParameterInfo parameter : method.parameters()) requirePath(path, parameter.location());
            }
        }
        for (MethodCall call : calls) requirePath(path, call.location());
        for (AnalysisDiagnostic diagnostic : diagnostics)
            if (diagnostic.location().isPresent()) requirePath(path, diagnostic.location().get());
        // Related diagnostic locations may intentionally point to another source file.
    }

    private static void requirePath(String path, SourceLocation location) {
        if (!path.equals(location.path())) throw new IllegalArgumentException("Source element belongs to another file");
    }
}
