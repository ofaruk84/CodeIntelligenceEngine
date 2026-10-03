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

    /** Bounded BFS includes the start. Limits stop discovery, never change minimum distances. */
    public Selection select(MethodId start, Direction direction, int depth, int maxNodes) {
        if (depth < 0 || maxNodes < 1) throw new IllegalArgumentException("Invalid traversal bounds");
        var search = search(start, direction, Optional.empty(), depth, maxNodes);
        return new Selection(search.distances(), search.depthLimited(), search.nodeLimited());
    }

    public record Selection(Map<MethodId, Integer> distances, boolean depthLimited, boolean nodeLimited) {
        public Selection { distances = Collections.unmodifiableMap(new LinkedHashMap<>(distances)); }
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
        return search(start, direction, target, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private Search search(MethodId start, Direction direction, Optional<MethodId> target, int depth, int maxNodes) {
        requireKnown(start);
        Objects.requireNonNull(direction, "direction");
        var distances = new LinkedHashMap<MethodId, Integer>();
        var parents = new HashMap<MethodId, MethodId>();
        var queue = new ArrayDeque<MethodId>();
        distances.put(start, 0);
        queue.add(start);
        boolean depthLimited = false, nodeLimited = false;
        while (!queue.isEmpty()) {
            var current = queue.remove();
            if (target.filter(current::equals).isPresent()) break;
            var neighbors = direction == Direction.FORWARD
                    ? graph.callees(current) : graph.callers(current);
            for (var next : neighbors.stream().sorted(Comparator.comparing(MethodId::value)).toList()) {
                if (distances.containsKey(next)) continue;
                if (distances.get(current) == depth) { depthLimited = true; continue; }
                if (distances.size() == maxNodes) { nodeLimited = true; continue; }
                distances.put(next, distances.get(current) + 1);
                parents.put(next, current);
                queue.add(next);
            }
        }
        return new Search(distances, parents, depthLimited, nodeLimited);
    }

    private void requireKnown(MethodId id) {
        Objects.requireNonNull(id, "id");
        if (!graph.contains(id)) throw new IllegalArgumentException("Unknown graph node: " + id.value());
    }

    private record Search(Map<MethodId, Integer> distances, Map<MethodId, MethodId> parents, boolean depthLimited, boolean nodeLimited) {}
}
