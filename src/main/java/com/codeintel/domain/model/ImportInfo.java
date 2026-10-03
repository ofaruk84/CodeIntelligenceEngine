package com.codeintel.domain.model;

import java.util.Objects;

/** Qualified name excludes the trailing .*; flags preserve static and on-demand imports. */
public record ImportInfo(String qualifiedName, boolean isStatic, boolean onDemand, SourceLocation location) {
    public ImportInfo {
        qualifiedName = ModelChecks.name(qualifiedName);
        Objects.requireNonNull(location, "location");
    }
}
