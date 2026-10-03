package com.codeintel.domain.model;

import java.util.Objects;
import java.util.Optional;

/** One lexical call site. A missing caller represents a field/initializer call without an invented method. */
public record MethodCall(String rawExpression, String name, Optional<String> scope, SourceLocation location,
                         Optional<MethodId> caller, Optional<MethodId> target,
                         ResolutionStatus status, Optional<ResolutionFailure> failure) {
    public MethodCall {
        rawExpression = ModelChecks.text(rawExpression, "raw expression");
        name = ModelChecks.text(name, "call name");
        Objects.requireNonNull(scope, "scope");
        scope.ifPresent(s -> ModelChecks.text(s, "scope"));
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failure, "failure");
        if (status == ResolutionStatus.RESOLVED) {
            if (target.isEmpty() || failure.isPresent())
                throw new IllegalArgumentException("Resolved calls require a target and no failure");
        } else {
            if (target.isPresent() || failure.isEmpty())
                throw new IllegalArgumentException("Unresolved or ambiguous calls require failure details and no target");
            boolean evidence = !failure.orElseThrow().competingTargets().isEmpty();
            if ((status == ResolutionStatus.AMBIGUOUS) != evidence)
                throw new IllegalArgumentException("Only AMBIGUOUS calls have competing-target evidence");
        }
    }
}
