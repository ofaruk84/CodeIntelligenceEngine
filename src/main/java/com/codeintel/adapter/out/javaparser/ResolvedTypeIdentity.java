package com.codeintel.adapter.out.javaparser;

import com.codeintel.domain.model.TypeReference;
import com.github.javaparser.resolution.types.ResolvedType;

/** Shared signature normalization for source declarations and external targets. */
final class ResolvedTypeIdentity {
    private ResolvedTypeIdentity() {}

    static TypeReference type(ResolvedType original) {
        ResolvedType erased = original.erasure();
        int dimensions = 0;
        while (erased.isArray()) {
            dimensions++;
            erased = erased.asArrayType().getComponentType();
        }
        String name = erased.isReferenceType() ? erased.asReferenceType().getQualifiedName() : erased.describe();
        return new TypeReference(name, dimensions, true);
    }
}
