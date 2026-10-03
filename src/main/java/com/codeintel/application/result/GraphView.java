package com.codeintel.application.result;

import com.codeintel.domain.graph.GraphEdge;
import com.codeintel.domain.model.MethodId;
import com.codeintel.application.service.GraphViewOptions;
import java.util.*;

/** Selected nodes and induced caller-to-callee edges, with explicit truncation reasons. */
public record GraphView(MethodId target, GraphViewOptions options, Map<MethodId, Integer> distances,
                        List<GraphEdge> edges, Set<MethodId> externalTargets,
                        boolean depthLimited, boolean nodeLimited, boolean edgeLimited) {
    public GraphView {
        distances = Collections.unmodifiableMap(new LinkedHashMap<>(distances));
        edges = List.copyOf(edges); externalTargets = Set.copyOf(externalTargets);
    }
}
