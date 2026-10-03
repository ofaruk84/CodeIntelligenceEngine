package com.codeintel.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Structured diagnostic; related locations retain evidence such as duplicate declarations. */
public record AnalysisDiagnostic(DiagnosticSeverity severity, String code, String message,
                                 Optional<SourceLocation> location, List<SourceLocation> relatedLocations) {
    public AnalysisDiagnostic {
        Objects.requireNonNull(severity, "severity");
        code = ModelChecks.text(code, "diagnostic code");
        message = ModelChecks.text(message, "diagnostic message");
        Objects.requireNonNull(location, "location");
        relatedLocations = List.copyOf(relatedLocations);
    }

    /** Builds a diagnostic for an already-detected collision; detection belongs to collection. */
    public static AnalysisDiagnostic duplicateDeclaration(String canonicalIdentity, SourceLocation first,
                                                           SourceLocation conflicting) {
        ModelChecks.text(canonicalIdentity, "canonical identity");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(conflicting, "conflicting");
        if (first.equals(conflicting)) throw new IllegalArgumentException("Duplicate declarations need distinct locations");
        return new AnalysisDiagnostic(DiagnosticSeverity.ERROR, "DUPLICATE_DECLARATION",
                "Multiple declarations share canonical identity: " + canonicalIdentity,
                Optional.of(first), List.of(conflicting));
    }
}
