package com.codeintel.domain.graph;

import com.codeintel.adapter.out.graph.InMemoryCodeGraph;
import com.codeintel.domain.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CodeGraphTest {
    private final SourceLocation location = new SourceLocation("Example.java", 1, 1, 1, 10);
    private MethodId id(String name) { return MethodId.canonical(SymbolId.canonical("example.Example"), name, List.of()); }
    private MethodCall call(MethodId from, MethodId to) {
        return new MethodCall("invoke()", to.name(), Optional.empty(), location, Optional.ofNullable(from),
                Optional.of(to), ResolutionStatus.RESOLVED, Optional.empty());
    }
    private MethodNode definition(MethodId id) {
        return new MethodNode(id, CallableKind.METHOD, Optional.of(TypeReference.resolved("void")),
                List.of(), Set.of(), List.of(), location);
    }
    private InMemoryCodeGraph graph(MethodCall... calls) { return new InMemoryCodeGraph(List.of(), List.of(calls)); }

    @Test void deduplicatesIndexesPreservesInputsAndIncludesIsolatedAndExternalNodes() {
        var a = id("a"); var b = id("b"); var isolated = id("isolated");
        var calls = new ArrayList<>(List.of(call(a, b), call(a, b)));
        var definitions = new ArrayList<>(List.of(definition(a), definition(isolated)));
        var graph = new InMemoryCodeGraph(definitions, calls);
        assertEquals(2, calls.size());
        assertEquals(Set.of(new GraphEdge(a, b)), graph.edges());
        assertEquals(Set.of(b), graph.callees(a));
        assertEquals(Set.of(a), graph.callers(b));
        assertEquals(Set.of(a, b, isolated), graph.nodes());
        assertTrue(graph.callees(isolated).isEmpty());
        calls.clear(); definitions.clear();
        assertEquals(1, graph.edges().size());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.edges().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.callees(a).clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.callers(b).clear());
    }

    @Test void excludesUnresolvedAmbiguousAndCallerlessOccurrences() {
        var a = id("a"); var b = id("b"); var c = id("c");
        var unresolved = new MethodCall("missing()", "missing", Optional.empty(), location, Optional.of(a),
                Optional.empty(), ResolutionStatus.UNRESOLVED,
                Optional.of(new ResolutionFailure("MISSING", "Missing type", List.of())));
        var ambiguous = new MethodCall("select(null)", "select", Optional.empty(), location, Optional.of(a),
                Optional.empty(), ResolutionStatus.AMBIGUOUS,
                Optional.of(new ResolutionFailure("AMBIGUOUS", "Competing targets", List.of(b, c))));
        var graph = graph(unresolved, ambiguous, call(null, b));
        assertTrue(graph.edges().isEmpty());
        assertEquals(Set.of(b), graph.nodes());
        assertTrue(graph.callers(b).isEmpty());
        assertEquals("Missing type", unresolved.failure().orElseThrow().message());
    }

    @Test void retainsExactOverloadedNestedConstructorAndFallbackIdentities() {
        var owner = SymbolId.canonical("example.Outer$Nested");
        var integer = MethodId.canonical(owner, "run", List.of(TypeReference.resolved("int")));
        var string = MethodId.canonical(owner, "run", List.of(TypeReference.resolved("java.lang.String")));
        var constructor = MethodId.canonical(owner, "<init>", List.of());
        var fallback = new MethodId(owner, "run", List.of(TypeReference.unresolved("Missing")), Optional.of(location));
        var graph = graph(call(constructor, integer), call(constructor, string), call(constructor, fallback));
        assertEquals(Set.of(integer, string, fallback), graph.callees(constructor));
        assertEquals(4, graph.nodes().size());
    }

    @Test void handlesCyclesSelfLoopsDistancesAndDeterministicShortestPaths() {
        var a = id("a"); var b = id("b"); var c = id("c"); var d = id("d"); var e = id("e");
        var calls = List.of(call(a, c), call(c, d), call(a, a), call(d, a), call(a, b), call(b, d), call(d, e), call(a, e));
        var graph = new InMemoryCodeGraph(List.of(), calls);
        var traversal = new GraphTraversal(graph);
        assertEquals(List.of(a, b, c, d, e), new ArrayList<>(graph.nodes()));
        assertEquals(List.of(a, b, c, e), new ArrayList<>(graph.callees(a)));
        assertEquals(Map.of(b, 1, c, 1, e, 1, d, 2), traversal.reachable(a, GraphTraversal.Direction.FORWARD));
        assertEquals(List.of(b, c, e, d), new ArrayList<>(traversal.reachable(a, GraphTraversal.Direction.FORWARD).keySet()));
        assertEquals(Map.of(d, 1, b, 2, c, 2), traversal.reachable(a, GraphTraversal.Direction.REVERSE));
        assertEquals(Optional.of(List.of(a, b, d)), traversal.shortestPath(a, d));
        assertEquals(Optional.of(List.of(a)), traversal.shortestPath(a, a));
        assertTrue(graph.callees(a).contains(a));
        assertThrows(UnsupportedOperationException.class, () -> traversal.reachable(a, GraphTraversal.Direction.FORWARD).clear());
        assertThrows(UnsupportedOperationException.class, () -> traversal.shortestPath(a, d).orElseThrow().clear());
        var shuffled = new ArrayList<>(calls); Collections.reverse(shuffled);
        var other = new InMemoryCodeGraph(List.of(), shuffled);
        assertEquals(new ArrayList<>(graph.edges()), new ArrayList<>(other.edges()));
        assertEquals(traversal.shortestPath(a, d), new GraphTraversal(other).shortestPath(a, d));
        for (var edge : graph.edges()) {
            assertTrue(graph.callees(edge.caller()).contains(edge.target()));
            assertTrue(graph.callers(edge.target()).contains(edge.caller()));
        }
    }

    @Test void distinguishesUnknownFromDisconnectedAndEmptyGraphs() {
        var a = id("a"); var b = id("b"); var unknown = id("unknown");
        var graph = new InMemoryCodeGraph(List.of(definition(a), definition(b)), List.of());
        var traversal = new GraphTraversal(graph);
        assertEquals(Optional.empty(), traversal.shortestPath(a, b));
        assertEquals(Optional.of(List.of(a)), traversal.shortestPath(a, a));
        assertTrue(traversal.reachable(a, GraphTraversal.Direction.FORWARD).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> graph.callees(unknown));
        assertThrows(IllegalArgumentException.class, () -> graph.callers(unknown));
        assertThrows(IllegalArgumentException.class, () -> traversal.shortestPath(a, unknown));
        assertThrows(IllegalArgumentException.class, () -> traversal.shortestPath(unknown, a));
        assertThrows(IllegalArgumentException.class, () -> traversal.reachable(unknown, GraphTraversal.Direction.REVERSE));
        assertFalse(graph.contains(unknown));
        assertTrue(graph().nodes().isEmpty());
    }
}
