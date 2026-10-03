package com.codeintel.domain.graph;

import com.codeintel.adapter.out.filesystem.FileSystemRepositoryScanner;
import com.codeintel.adapter.out.javaparser.JavaParserSourceAnalyzer;
import com.codeintel.adapter.out.graph.InMemoryCodeGraph;
import com.codeintel.domain.model.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParsedGraphIntegrationTest {
    @Test void buildsFromResolvedCommerceFixtureWithoutChangingAnalysisResults() throws Exception {
        var root = Path.of(Objects.requireNonNull(getClass().getResource("/fixtures/commerce")).toURI());
        var units = new JavaParserSourceAnalyzer().analyze(new FileSystemRepositoryScanner().scan(root, List.of()));
        var original = List.copyOf(units);
        var calls = units.stream().flatMap(unit -> unit.calls().stream()).toList();
        var graph = InMemoryCodeGraph.fromSources(units);
        var expected = new HashSet<GraphEdge>();
        for (var call : calls) if (call.status() == ResolutionStatus.RESOLVED && call.caller().isPresent())
            expected.add(new GraphEdge(call.caller().orElseThrow(), call.target().orElseThrow()));
        assertEquals(expected, graph.edges());
        assertEquals(original, units);
        var repeated = calls.stream().filter(call -> call.rawExpression().equals("payments.pay(orderId)")
                && call.caller().orElseThrow().name().equals("place")).toList();
        assertEquals(2, repeated.size());
        var edge = new GraphEdge(repeated.getFirst().caller().orElseThrow(), repeated.getFirst().target().orElseThrow());
        assertEquals(1, graph.edges().stream().filter(edge::equals).count());
        var definitions = units.stream().flatMap(unit -> unit.types().stream())
                .flatMap(type -> type.methods().stream()).map(MethodNode::id).toList();
        assertTrue(graph.nodes().containsAll(definitions));
        var external = calls.stream().filter(call -> call.rawExpression().equals("emptyList()"))
                .findFirst().orElseThrow().target().orElseThrow();
        assertTrue(graph.contains(external));
        assertFalse(definitions.contains(external));
        assertTrue(calls.stream().anyMatch(call -> call.status() == ResolutionStatus.UNRESOLVED));
        // Inspect fixture definitions by identity, without synthesizing production graph data.
        var cycleEdge = graph.edges().stream().filter(value -> value.caller().declaringType().value().endsWith("CycleA"))
                .findFirst().orElseThrow();
        assertFalse(new GraphTraversal(graph).reachable(cycleEdge.caller(), GraphTraversal.Direction.FORWARD)
                .containsKey(cycleEdge.caller()));
    }
}
