package com.codeintel.domain.model;

import java.util.Objects;
import java.util.Set;

/** One declared field; a multi-variable declaration produces one value per variable. */
public record FieldInfo(String name, TypeReference type, Set<String> modifiers, SourceLocation location) {
    public FieldInfo {
        name = ModelChecks.simpleName(name);
        Objects.requireNonNull(type, "type");
        if (type.name().equals("void")) throw new IllegalArgumentException("A field cannot be void");
        modifiers = ModelChecks.modifiers(modifiers);
        Objects.requireNonNull(location, "location");
    }
}
