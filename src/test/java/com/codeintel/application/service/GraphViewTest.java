package com.codeintel.application.service;

import com.codeintel.adapter.out.graph.InMemoryCodeGraph;
import com.codeintel.application.result.*;
import com.codeintel.domain.graph.*;
import com.codeintel.domain.model.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphViewTest {
    private final SourceLocation location = new SourceLocation("Graph.java", 1, 1, 1, 10);
    private MethodId id(String name) { return MethodId.canonical(SymbolId.canonical("example.Graph"), name, List.of()); }
    private MethodCall call(MethodId a, MethodId b) {
        return new MethodCall("call()", b.name(), Optional.empty(), location, Optional.of(a), Optional.of(b), ResolutionStatus.RESOLVED, Optional.empty());
    }
    private CodeIntelligenceService service(List<MethodCall> calls) {
        var graph = new InMemoryCodeGraph(List.of(), calls);
        return new CodeIntelligenceService(new AnalysisSnapshot(new RepositorySources(Path.of("."), List.of(), List.of(), List.of(), false), List.of(), graph));
    }
    private GraphViewOptions options(GraphTraversal.Direction direction, int depth, int nodes, int edges) { return new GraphViewOptions(direction, depth, nodes, edges); }
    @Test void bfsMinimumDepthCyclesSharedNodesAndInducedEdges() {
        var a = id("a"); var b = id("b"); var c = id("c"); var d = id("d"); var e = id("e");
        var calls = List.of(call(a, c), call(a, b), call(b, d), call(c, d), call(d, a), call(a, a), call(a, e), call(d, e));
        var service = service(calls);
        var options = options(GraphTraversal.Direction.FORWARD, 2, 20, 20);
        var view = service.graphView(a.value(), options);
        assertEquals(Map.of(a, 0, b, 1, c, 1, e, 1, d, 2), view.distances());
        assertEquals(List.of(a, b, c, e, d), List.copyOf(view.distances().keySet()));
        assertEquals(8, view.edges().size());
        assertFalse(view.depthLimited()); assertFalse(view.nodeLimited()); assertFalse(view.edgeLimited());
        assertTrue(view.edges().contains(new GraphEdge(a, a)));
        var shuffled = new ArrayList<>(calls); Collections.reverse(shuffled);
        assertEquals(view, service(shuffled).graphView(a.value(), options));
        assertThrows(UnsupportedOperationException.class, () -> view.distances().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.edges().clear());
    }
    @Test void reverseSelectionKeepsOriginalArrowsAndEachBoundIsExplicit() {
        var a = id("a"); var b = id("b"); var c = id("c"); var d = id("d");
        var service = service(List.of(call(a,b), call(b,c), call(c,d), call(d,d)));
        var reverse = service.graphView(c.value(), options(GraphTraversal.Direction.REVERSE, 1, 20, 20));
        assertEquals(Map.of(c,0,b,1), reverse.distances());
        assertEquals(List.of(new GraphEdge(b,c)), reverse.edges()); assertTrue(reverse.depthLimited());
        var nodes = service.graphView(a.value(), options(GraphTraversal.Direction.FORWARD, 10, 2, 20));
        assertEquals(2, nodes.distances().size()); assertTrue(nodes.nodeLimited());
        var edges = service.graphView(a.value(), options(GraphTraversal.Direction.FORWARD, 10, 20, 1));
        assertEquals(1, edges.edges().size()); assertTrue(edges.edgeLimited());
        var zero = service.graphView(d.value(), options(GraphTraversal.Direction.FORWARD, 0, 1, 1));
        assertEquals(Map.of(d,0), zero.distances()); assertEquals(List.of(new GraphEdge(d,d)), zero.edges());
        assertFalse(zero.depthLimited()); assertFalse(zero.edgeLimited());
        assertThrows(UnknownMethodException.class, () -> service.graphView("missing", GraphViewOptions.defaults()));
    }
    @Test void exactExternalOverloadNestedConstructorFallbackAndIsolatedIds() {
        var owner = SymbolId.canonical("example.Outer$Inner");
        var constructor = MethodId.canonical(owner, "<init>", List.of());
        var first = MethodId.canonical(owner, "run", List.of(TypeReference.resolved("int")));
        var second = MethodId.canonical(owner, "run", List.of(TypeReference.resolved("java.lang.String")));
        var fallback = new MethodId(owner, "run", List.of(TypeReference.unresolved("Missing")), Optional.of(location));
        var service = service(List.of(call(constructor, first), call(constructor, second), call(constructor, fallback)));
        var view = service.graphView(constructor.value(), options(GraphTraversal.Direction.FORWARD, 1, 10, 10));
        assertEquals(Set.of(constructor, first, second, fallback), view.distances().keySet());
        assertEquals(view.distances().keySet(), view.externalTargets());
        assertEquals(Map.of(first,0), service.graphView(first.value(), options(GraphTraversal.Direction.FORWARD, 10, 10, 10)).distances());
        for (int[] bounds : List.of(new int[]{-1,1,1}, new int[]{101,1,1}, new int[]{1,0,1}, new int[]{1,10001,1}, new int[]{1,1,0}, new int[]{1,1,50001}))
            assertThrows(IllegalArgumentException.class, () -> options(GraphTraversal.Direction.FORWARD, bounds[0], bounds[1], bounds[2]));
    }
}
