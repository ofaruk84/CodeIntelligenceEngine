package com.codeintel.adapter.in.cli;

import com.codeintel.application.result.AnalysisSnapshot;
import com.codeintel.domain.model.SourceLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Versioned JSON Lines evidence export. Never overwrites existing user data. */
final class DiagnosticsWriter {
    private DiagnosticsWriter() {}
    static void write(Path path, AnalysisSnapshot snapshot) throws IOException {
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            var coverage = snapshot.coverage();
            writer.write(json(row("kind", "summary", "schemaVersion", 1,
                    "repository", snapshot.repository().repository().toString(),
                    "partialScan", coverage.partialScan(), "partialSources", coverage.partialSources(),
                    "resolved", coverage.resolvedCallCount(), "unresolved", coverage.unresolvedCalls().size(),
                    "ambiguous", coverage.ambiguousCalls().size(),
                    "externalTargets", coverage.externalTargets().stream().map(id -> id.value()).toList())) + "\n");
            for (var diagnostic : coverage.scanDiagnostics())
                writer.write(json(row("kind", "scanDiagnostic", "code", diagnostic.code(),
                        "path", diagnostic.path().toString().replace('\\', '/'), "message", diagnostic.message())) + "\n");
            for (var unit : snapshot.sources()) {
                for (var diagnostic : unit.diagnostics())
                    writer.write(json(row("kind", "sourceDiagnostic", "severity", diagnostic.severity().name(),
                            "code", diagnostic.code(), "path", unit.path(), "message", diagnostic.message(),
                            "location", diagnostic.location().map(DiagnosticsWriter::location).orElse(null),
                            "relatedLocations", diagnostic.relatedLocations().stream().map(DiagnosticsWriter::location).toList())) + "\n");
                for (var call : unit.calls())
                    writer.write(json(row("kind", "call", "status", call.status().name(),
                            "location", location(call.location()), "expression", call.rawExpression(),
                            "name", call.name(), "scope", call.scope().orElse(null),
                            "caller", call.caller().map(id -> id.value()).orElse(null),
                            "target", call.target().map(id -> id.value()).orElse(null),
                            "failure", call.failure().map(f -> row("category", f.category(), "message", f.message(),
                                    "competingTargets", f.competingTargets().stream().map(id -> id.value()).toList())).orElse(null))) + "\n");
            }
        }
    }
    private static Map<String, Object> location(SourceLocation value) {
        return row("path", value.path(), "startLine", value.startLine(), "startColumn", value.startColumn(),
                "endLine", value.endLine(), "endColumn", value.endColumn());
    }
    private static Map<String, Object> row(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream()
                .map(entry -> json(entry.getKey()) + ":" + json(entry.getValue())).toList()) + "}";
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(DiagnosticsWriter::json).toList()) + "]";
        var result = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32 || Character.isSurrogate(c)) result.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
