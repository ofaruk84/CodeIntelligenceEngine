package com.codeintel.application.service;

import com.codeintel.adapter.out.graph.InMemoryCodeGraph;
import com.codeintel.application.result.*;
import com.codeintel.domain.model.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodeIntelligenceServiceTest {
    private final SymbolId owner = SymbolId.canonical("example.Outer$Inner");
    private final MethodId a = id("a"), b = id("b"), c = id("c"), d = id("d"), isolated = id("isolated");
    private final MethodId external = MethodId.canonical(SymbolId.canonical("java.util.Collections"), "emptyList", List.of());
    private MethodId id(String name) { return MethodId.canonical(owner, name, List.of()); }
    private SourceLocation location() { return new SourceLocation("Example.java", 1, 1, 1, 20); }
    private MethodNode definition(MethodId id) {
        var parameters = new ArrayList<ParameterInfo>();
        for (var type : id.parameterTypes()) parameters.add(new ParameterInfo("value", type, false, Set.of(), location()));
        return new MethodNode(id, id.name().equals("<init>") ? CallableKind.CONSTRUCTOR : CallableKind.METHOD,
                id.name().equals("<init>") ? Optional.empty() : Optional.of(TypeReference.resolved("void")),
                parameters, Set.of(), List.of(), location());
    }
    private MethodCall call(MethodId from, MethodId to) {
        return new MethodCall("call()", to.name(), Optional.empty(), location(), Optional.of(from),
                Optional.of(to), ResolutionStatus.RESOLVED, Optional.empty());
    }
    private CodeIntelligenceService service() {
        var overload = MethodId.canonical(owner, "a", List.of(TypeReference.resolved("java.lang.String")));
        var constructor = id("<init>");
        var methods = List.of(a, b, c, d, isolated, overload, constructor).stream().map(this::definition).toList();
        var type = new ClassNode(owner, Optional.of("Inner"), TypeKind.CLASS, ClassNode.Nesting.TOP_LEVEL,
                Optional.empty(), Set.of(), List.of(), List.of(), methods, location());
        var calls = List.of(call(a, b), call(a, b), call(a, c), call(b, d), call(c, d),
                call(d, a), call(d, d), call(c, external));
        var unit = new SourceUnit("Example.java", "example", List.of(), List.of(type), calls, List.of(), false);
        return new CodeIntelligenceService(new AnalysisSnapshot(new RepositorySources(Path.of("."),
                List.of(Path.of("Example.java")), List.of(), List.of(), false), List.of(unit),
                InMemoryCodeGraph.fromSources(List.of(unit))));
    }
    @Test void searchesDefinitionsIncludingOverloadsAndConstructorsInIdOrder() {
        var service = service();
        var all = service.searchSymbol("").symbols();
        assertEquals(8, all.size());
        assertEquals(all.stream().map(SymbolSearchResult.Symbol::id).sorted().toList(),
                all.stream().map(SymbolSearchResult.Symbol::id).toList());
        assertEquals(2, service.searchSymbol("#A(").symbols().size());
        assertEquals(1, service.searchSymbol("<INIT>").symbols().size());
        assertEquals(all, service.searchSymbol("INNER").symbols());
        assertThrows(UnsupportedOperationException.class, () -> all.clear());
        assertEquals(a, service.getMethod(a.value()).orElseThrow().id());
        assertTrue(service.getMethod(external.value()).isEmpty());
        assertTrue(service.getMethod("missing").isEmpty());
    }
    @Test void directNeighborsAreUniqueSortedAndRetainSelfEdges() {
        var service = service();
        assertEquals(List.of(b, c), service.findCallees(a.value()));
        assertEquals(List.of(b, c, d), service.findCallers(d.value()));
        assertEquals(List.of(a, d), service.findCallees(d.value()));
        assertEquals(List.of(), service.findCallers(isolated.value()));
        assertEquals(List.of(), service.findCallees(external.value()));
        assertThrows(UnsupportedOperationException.class, () -> service.findCallers(d.value()).clear());
    }
    @Test void dependenciesAndPathsReuseBfsWithMinimumDistancesAndDeterministicTies() {
        var service = service();
        var dependencies = service.findDependencies(a.value()).minimumDistances();
        assertEquals(Map.of(b, 1, c, 1, d, 2, external, 2), dependencies);
        assertFalse(dependencies.containsKey(a));
        assertEquals(List.of(a, b, d), service.findPath(a.value(), d.value()).path().orElseThrow());
        assertEquals(List.of(a), service.findPath(a.value(), a.value()).path().orElseThrow());
        assertTrue(service.findPath(a.value(), isolated.value()).path().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> dependencies.clear());
        assertThrows(UnsupportedOperationException.class, () -> service.findPath(a.value(), d.value()).path().orElseThrow().clear());
    }
    @Test void impactExcludesTargetAndAggregatesClassesWithMinimumDepths() {
        var impact = service().analyzeChangeImpact(d.value());
        assertEquals(List.of(b, c), impact.directCallers());
        assertEquals(List.of(a), impact.indirectCallers());
        assertEquals(Map.of(a, 2, b, 1, c, 1), impact.minimumDepths());
        assertEquals(List.of(owner), impact.affectedClasses());
        assertEquals(2, impact.maximumDepth());
        assertFalse(impact.affectedMethods().contains(d));
        assertEquals(0, service().analyzeChangeImpact(isolated.value()).maximumDepth());
        assertTrue(impact.coverage().externalTargets().contains(external));
        assertTrue(impact.coverage().incomplete());
        assertThrows(UnsupportedOperationException.class, () -> impact.minimumDepths().clear());
        assertThrows(UnsupportedOperationException.class, () -> impact.affectedClasses().clear());
    }
    @Test void unknownGraphIdsHaveOneExplicitOutcomeAcrossQueries() {
        var service = service();
        assertThrows(UnknownMethodException.class, () -> service.findCallers("missing"));
        assertThrows(UnknownMethodException.class, () -> service.findCallees("missing"));
        assertThrows(UnknownMethodException.class, () -> service.findDependencies("missing"));
        assertThrows(UnknownMethodException.class, () -> service.analyzeChangeImpact("missing"));
        assertEquals(List.of("missing", "other"), assertThrows(UnknownMethodException.class,
                () -> service.findPath("other", "missing")).ids());
    }
    @Test void fallbackIdsAndAmbiguityEvidenceRemainIntactWithoutTargetClaims() {
        var fallback = new MethodId(owner, "fallback", List.of(TypeReference.unresolved("Missing")),
                Optional.of(location()));
        var type = new ClassNode(owner, Optional.of("Inner"), TypeKind.CLASS, ClassNode.Nesting.TOP_LEVEL,
                Optional.empty(), Set.of(), List.of(), List.of(),
                List.of(definition(a), definition(b), definition(fallback)), location());
        var ambiguous = new MethodCall("unknown()", "unknown", Optional.empty(), location(), Optional.of(b),
                Optional.empty(), ResolutionStatus.AMBIGUOUS,
                Optional.of(new ResolutionFailure("COMPETING_TARGETS", "Two candidates", List.of(a, fallback))));
        var noCaller = new MethodCall("emptyList()", "emptyList", Optional.empty(), location(), Optional.empty(),
                Optional.of(external), ResolutionStatus.RESOLVED, Optional.empty());
        var unit = new SourceUnit("Example.java", "example", List.of(), List.of(type),
                List.of(call(b, a), ambiguous, noCaller), List.of(), false);
        var service = new CodeIntelligenceService(new AnalysisSnapshot(new RepositorySources(Path.of("."),
                List.of(), List.of(), List.of(), false), List.of(unit), InMemoryCodeGraph.fromSources(List.of(unit))));
        assertEquals(fallback, service.getMethod(fallback.value()).orElseThrow().id());
        var impact = service.analyzeChangeImpact(a.value());
        assertEquals(List.of(ambiguous), impact.coverage().ambiguousCalls());
        assertEquals(List.of(ambiguous), impact.unresolvedCallsInAffectedMethods());
        assertEquals(List.of(noCaller), impact.coverage().callsWithoutCaller());
        assertEquals(List.of(), service.findCallers(external.value()));
        assertTrue(service.findDependencies(fallback.value()).minimumDistances().isEmpty());
    }
}
