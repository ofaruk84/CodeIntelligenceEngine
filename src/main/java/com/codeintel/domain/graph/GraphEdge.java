package com.codeintel.domain.graph;

import com.codeintel.domain.model.MethodId;
import java.util.Objects;

/** One unique statically resolved caller-to-target relationship, not a call site. */
public record GraphEdge(MethodId caller, MethodId target) {
    public GraphEdge {
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(target, "target");
    }
}
