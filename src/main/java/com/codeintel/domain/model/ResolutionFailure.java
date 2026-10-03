package com.codeintel.domain.model;

import java.util.List;

/** Failure context; competing targets must come from resolver evidence, never guesses. */
public record ResolutionFailure(String category, String message, List<MethodId> competingTargets) {
    public ResolutionFailure {
        category = ModelChecks.text(category, "failure category");
        message = ModelChecks.text(message, "failure message");
        competingTargets = List.copyOf(competingTargets);
        if (!competingTargets.isEmpty()
                && (competingTargets.size() < 2 || competingTargets.stream().distinct().count() != competingTargets.size()))
            throw new IllegalArgumentException("Ambiguity evidence must contain at least two distinct targets");
    }
}
