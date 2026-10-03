package com.codeintel.domain.graph;

import com.codeintel.domain.model.MethodId;
import java.util.*;

/** Reusable iterative BFS; no application query or presentation policy. */
public final class GraphTraversal {
    public enum Direction { FORWARD, REVERSE }
    private final CodeGraph graph;

    public GraphTraversal(CodeGraph graph) { this.graph = Objects.requireNonNull(graph, "graph"); }

    /** Minimum distances in BFS discovery order; excludes the starting node even in cycles. */
    public Map<MethodId, Integer> reachable(MethodId start, Direction direction) {
        var search = search(start, direction, Optional.empty());
        var distances = new LinkedHashMap<>(search.distances());
        distances.remove(start);
        return Collections.unmodifiableMap(distances);
    }

    /** Empty means known but disconnected; unknown endpoints throw IllegalArgumentException. */
    public Optional<List<MethodId>> shortestPath(MethodId source, MethodId target) {
        requireKnown(target);
        var search = search(source, Direction.FORWARD, Optional.of(target));
        if (!search.distances().containsKey(target)) return Optional.empty();
        var path = new ArrayList<MethodId>();
        for (var current = target; current != null; current = search.parents().get(current)) path.add(current);
        Collections.reverse(path);
        return Optional.of(List.copyOf(path));
    }

    private Search search(MethodId start, Direction direction, Optional<MethodId> target) {
        requireKnown(start);
        Objects.requireNonNull(direction, "direction");
        var distances = new LinkedHashMap<MethodId, Integer>();
        var parents = new HashMap<MethodId, MethodId>();
        var queue = new ArrayDeque<MethodId>();
        distances.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            var current = queue.remove();
            if (target.filter(current::equals).isPresent()) break;
            var neighbors = direction == Direction.FORWARD
                    ? graph.callees(current) : graph.callers(current);
            for (var next : neighbors) {
                if (distances.containsKey(next)) continue;
                distances.put(next, distances.get(current) + 1);
                parents.put(next, current);
                queue.add(next);
            }
        }
        return new Search(distances, parents);
    }

    private void requireKnown(MethodId id) {
        Objects.requireNonNull(id, "id");
        if (!graph.contains(id)) throw new IllegalArgumentException("Unknown graph node: " + id.value());
    }

    private record Search(Map<MethodId, Integer> distances, Map<MethodId, MethodId> parents) {}
}
