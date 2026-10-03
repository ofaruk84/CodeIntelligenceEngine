package com.codeintel.adapter.out.graph;

import com.codeintel.domain.graph.*;
import com.codeintel.domain.model.*;
import java.util.*;

/** Immutable snapshot with forward and reverse indexes built once. */
public final class InMemoryCodeGraph implements CodeGraph {
    private static final Comparator<MethodId> ORDER = Comparator.comparing(MethodId::value);
    private final Set<MethodId> nodes;
    private final Set<GraphEdge> edges;
    private final Map<MethodId, Set<MethodId>> forward;
    private final Map<MethodId, Set<MethodId>> reverse;

    public InMemoryCodeGraph(Collection<MethodNode> definitions, Collection<MethodCall> calls) {
        var outgoing = new HashMap<MethodId, Set<MethodId>>();
        var incoming = new HashMap<MethodId, Set<MethodId>>();
        for (var definition : definitions) addNode(definition.id(), outgoing, incoming);
        for (var call : calls) {
            if (call.status() != ResolutionStatus.RESOLVED) continue;
            var target = call.target().orElseThrow();
            addNode(target, outgoing, incoming);
            if (call.caller().isEmpty()) continue;
            var caller = call.caller().orElseThrow();
            addNode(caller, outgoing, incoming);
            outgoing.get(caller).add(target);
            incoming.get(target).add(caller);
        }
        nodes = sorted(outgoing.keySet());
        forward = freeze(outgoing);
        reverse = freeze(incoming);
        var uniqueEdges = new LinkedHashSet<GraphEdge>();
        for (var caller : nodes) for (var target : forward.get(caller))
            uniqueEdges.add(new GraphEdge(caller, target));
        edges = Collections.unmodifiableSet(uniqueEdges);
    }

    /** Consumes existing analysis results without changing or retaining mutable input collections. */
    public static InMemoryCodeGraph fromSources(Collection<SourceUnit> sources) {
        var definitions = new ArrayList<MethodNode>();
        var calls = new ArrayList<MethodCall>();
        for (var source : sources) {
            source.types().forEach(type -> definitions.addAll(type.methods()));
            calls.addAll(source.calls());
        }
        return new InMemoryCodeGraph(definitions, calls);
    }

    private static void addNode(MethodId id, Map<MethodId, Set<MethodId>> forward,
                                Map<MethodId, Set<MethodId>> reverse) {
        forward.computeIfAbsent(id, ignored -> new HashSet<>());
        reverse.computeIfAbsent(id, ignored -> new HashSet<>());
    }

    private static Set<MethodId> sorted(Collection<MethodId> values) {
        var ordered = new ArrayList<>(values);
        ordered.sort(ORDER);
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    private static Map<MethodId, Set<MethodId>> freeze(Map<MethodId, Set<MethodId>> index) {
        var result = new HashMap<MethodId, Set<MethodId>>();
        index.forEach((id, neighbors) -> result.put(id, sorted(neighbors)));
        return Map.copyOf(result);
    }

    @Override public Set<MethodId> nodes() { return nodes; }
    @Override public Set<GraphEdge> edges() { return edges; }
    @Override public Set<MethodId> callees(MethodId caller) { return neighbors(forward, caller); }
    @Override public Set<MethodId> callers(MethodId callee) { return neighbors(reverse, callee); }

    private Set<MethodId> neighbors(Map<MethodId, Set<MethodId>> index, MethodId id) {
        Objects.requireNonNull(id, "id");
        var result = index.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown graph node: " + id.value());
        return result;
    }
}
