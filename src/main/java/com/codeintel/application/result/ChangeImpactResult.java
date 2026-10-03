package com.codeintel.application.result;

import com.codeintel.domain.model.*;
import java.util.*;

/** Observed reverse reachability; unresolved calls below originate only in affected methods. */
public record ChangeImpactResult(MethodId target, List<MethodId> directCallers,
        List<MethodId> indirectCallers, List<MethodId> affectedMethods, List<SymbolId> affectedClasses,
        Map<MethodId, Integer> minimumDepths, int maximumDepth, ResolutionCoverage coverage,
        List<MethodCall> unresolvedCallsInAffectedMethods) {
    public ChangeImpactResult {
        Objects.requireNonNull(target); Objects.requireNonNull(coverage);
        directCallers = List.copyOf(directCallers); indirectCallers = List.copyOf(indirectCallers);
        affectedMethods = List.copyOf(affectedMethods); affectedClasses = List.copyOf(affectedClasses);
        minimumDepths = Collections.unmodifiableMap(new LinkedHashMap<>(minimumDepths));
        unresolvedCallsInAffectedMethods = List.copyOf(unresolvedCallsInAffectedMethods);
    }
}
