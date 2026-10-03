package com.codeintel.application.result;

import com.codeintel.domain.graph.CodeGraph;
import com.codeintel.domain.model.*;
import java.util.*;

/** Immutable analysis data with definition and graph indexes reused by every query. */
public final class AnalysisSnapshot {
    private final RepositorySources repository;
    private final List<SourceUnit> sources;
    private final CodeGraph graph;
    private final Map<String, MethodNode> methods;
    private final Map<String, MethodId> graphIds;
    private final ResolutionCoverage coverage;
    public AnalysisSnapshot(RepositorySources repository, List<SourceUnit> sources, CodeGraph graph) {
        this.repository = Objects.requireNonNull(repository);
        this.sources = List.copyOf(sources);
        this.graph = Objects.requireNonNull(graph);
        var definitions = new TreeMap<String, MethodNode>();
        for (var unit : sources) for (var type : unit.types()) for (var method : type.methods()) {
            if (definitions.putIfAbsent(method.id().value(), method) != null)
                throw new IllegalArgumentException("Duplicate callable identity: " + method.id());
        }
        methods = Collections.unmodifiableMap(definitions);
        var ids = new TreeMap<String, MethodId>();
        graph.nodes().forEach(id -> ids.put(id.value(), id));
        if (!ids.keySet().containsAll(methods.keySet()))
            throw new IllegalArgumentException("Graph must include every source callable");
        graphIds = Collections.unmodifiableMap(ids);
        coverage = ResolutionCoverage.from(repository, this.sources, graph, methods.keySet());
    }
    public RepositorySources repository() { return repository; }
    public List<SourceUnit> sources() { return sources; }
    public CodeGraph graph() { return graph; }
    public Map<String, MethodNode> methods() { return methods; }
    public Map<String, MethodId> graphIds() { return graphIds; }
    public ResolutionCoverage coverage() { return coverage; }
}
