package com.codeintel.application.service;

import com.codeintel.domain.graph.GraphTraversal;
import java.util.Objects;

/** Presentation-independent selection limits. */
public record GraphViewOptions(GraphTraversal.Direction direction, int depth, int maxNodes, int maxEdges) {
    public GraphViewOptions {
        Objects.requireNonNull(direction);
        if (depth < 0 || depth > 100 || maxNodes < 1 || maxNodes > 10000 || maxEdges < 1 || maxEdges > 50000)
            throw new IllegalArgumentException("Graph bounds: depth 0..100, nodes 1..10000, edges 1..50000");
    }
    public static GraphViewOptions defaults() { return new GraphViewOptions(GraphTraversal.Direction.REVERSE, 3, 200, 500); }
}
