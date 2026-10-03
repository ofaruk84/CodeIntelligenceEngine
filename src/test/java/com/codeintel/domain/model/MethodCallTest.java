package com.codeintel.domain.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MethodCallTest {
    private static final SourceLocation LOCATION = new SourceLocation("Example.java", 5, 3, 5, 13);
    private static final MethodId FIRST = MethodId.canonical(SymbolId.canonical("example.Service"), "run", List.of());
    private static final MethodId SECOND = MethodId.canonical(SymbolId.canonical("other.Service"), "run", List.of());

    private static MethodCall call(ResolutionStatus status, Optional<MethodId> target, Optional<ResolutionFailure> failure) {
        return new MethodCall("service.run()", "run", Optional.of("service"), LOCATION,
                Optional.of(FIRST), target, status, failure);
    }

    @Test void preservesRawCallContextAndResolvedExternalTargetWithoutInventingDefinition() {
        var site = call(ResolutionStatus.RESOLVED, Optional.of(SECOND), Optional.empty());
        assertEquals("service.run()", site.rawExpression());
        assertEquals("run", site.name());
        assertEquals(Optional.of("service"), site.scope());
        assertEquals(LOCATION, site.location());
        assertEquals(Optional.of(FIRST), site.caller());
        assertEquals(Optional.of(SECOND), site.target());
    }

    @Test void fieldInitializerCallCanHaveNoCallerAndRepeatedSitesAreRetained() {
        var first = new MethodCall("run()", "run", Optional.empty(), LOCATION, Optional.empty(),
                Optional.of(FIRST), ResolutionStatus.RESOLVED, Optional.empty());
        var second = new MethodCall("run()", "run", Optional.empty(), new SourceLocation("Example.java", 8, 3, 8, 13),
                Optional.empty(), Optional.of(FIRST), ResolutionStatus.RESOLVED, Optional.empty());
        var unit = new SourceUnit("Example.java", "", List.of(), List.of(), List.of(first, second), List.of(), false);
        assertTrue(first.caller().isEmpty());
        assertTrue(first.scope().isEmpty());
        assertNotEquals(first, second);
        assertEquals(List.of(first, second), unit.calls());
    }

    @Test void genericResolverFailureIsUnresolvedAndRetainsDetails() {
        var failure = new ResolutionFailure("RESOLVER_EXCEPTION", "Missing type: thirdparty.Client", List.of());
        var site = call(ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.of(failure));
        assertEquals(ResolutionStatus.UNRESOLVED, site.status());
        assertEquals(failure, site.failure().orElseThrow());
        assertTrue(site.target().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> call(ResolutionStatus.AMBIGUOUS,
                Optional.empty(), Optional.of(failure)));
    }

    @Test void ambiguityRequiresTwoDistinctCandidatesAndCopiesEvidence() {
        var candidates = new ArrayList<>(List.of(FIRST, SECOND));
        var failure = new ResolutionFailure("COMPETING_TARGETS", "Resolver reported competing declarations", candidates);
        candidates.clear();
        var site = call(ResolutionStatus.AMBIGUOUS, Optional.empty(), Optional.of(failure));
        assertEquals(List.of(FIRST, SECOND), site.failure().orElseThrow().competingTargets());
        assertThrows(UnsupportedOperationException.class, () -> failure.competingTargets().clear());
        assertThrows(IllegalArgumentException.class, () -> new ResolutionFailure("COMPETING_TARGETS", "evidence", List.of(FIRST)));
        assertThrows(IllegalArgumentException.class, () -> new ResolutionFailure("COMPETING_TARGETS", "evidence", List.of(FIRST, FIRST)));
        assertThrows(IllegalArgumentException.class, () -> call(ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.of(failure)));
    }

    @Test void inconsistentResolutionStatesAreRejected() {
        var failure = new ResolutionFailure("MISSING_SYMBOL", "Could not resolve run", List.of());
        assertThrows(IllegalArgumentException.class, () -> call(ResolutionStatus.RESOLVED, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> call(ResolutionStatus.RESOLVED, Optional.of(FIRST), Optional.of(failure)));
        assertThrows(IllegalArgumentException.class, () -> call(ResolutionStatus.UNRESOLVED, Optional.of(FIRST), Optional.of(failure)));
        assertThrows(IllegalArgumentException.class, () -> call(ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty()));
    }

    @Test void constructorExpressionIsPreservedVerbatim() {
        var target = MethodId.canonical(SymbolId.canonical("example.Service"), "<init>", List.of());
        var site = new MethodCall("new Service( /* note */ )", "Service", Optional.empty(), LOCATION,
                Optional.of(FIRST), Optional.of(target), ResolutionStatus.RESOLVED, Optional.empty());
        assertEquals("new Service( /* note */ )", site.rawExpression());
        assertEquals("<init>", site.target().orElseThrow().name());
    }
}
