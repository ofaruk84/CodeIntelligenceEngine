package com.codeintel.domain.graph;

import com.codeintel.domain.model.MethodId;
import java.util.Set;

/** Read-only directed call graph. Collections iterate in canonical ID order. */
public interface CodeGraph {
    Set<MethodId> nodes();
    Set<GraphEdge> edges();
    default boolean contains(MethodId id) { return nodes().contains(id); }
    /** Unknown IDs are rejected; known isolated nodes return an empty set. */
    Set<MethodId> callees(MethodId caller);
    Set<MethodId> callers(MethodId callee);
}
