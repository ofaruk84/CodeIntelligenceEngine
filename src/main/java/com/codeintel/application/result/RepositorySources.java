package com.codeintel.application.result;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Paths are normalized; files, roots and diagnostic paths are repository-relative. */
public record RepositorySources(Path repository, List<Path> files, List<Path> sourceRoots,
                                List<Diagnostic> diagnostics, boolean partial) {
    public RepositorySources {
        Objects.requireNonNull(repository, "repository");
        files = List.copyOf(files);
        sourceRoots = List.copyOf(sourceRoots);
        diagnostics = List.copyOf(diagnostics);
    }

    /** Scan diagnostics have paths, not invented source line ranges. */
    public record Diagnostic(String code, Path path, String message) {
        public Diagnostic {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(message, "message");
            if (code.isBlank() || message.isBlank()) {
                throw new IllegalArgumentException("Diagnostic code and message must not be blank");
            }
        }
    }
}
