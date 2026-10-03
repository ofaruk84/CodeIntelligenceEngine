package com.codeintel.domain.model;

import java.util.Objects;
import java.util.Set;

/** Parameter type is already normalized: a varargs parameter stores an array type. */
public record ParameterInfo(String name, TypeReference type, boolean varargs,
                            Set<String> modifiers, SourceLocation location) {
    public ParameterInfo {
        name = ModelChecks.simpleName(name);
        Objects.requireNonNull(type, "type");
        if (type.name().equals("void")) throw new IllegalArgumentException("A parameter cannot be void");
        if (varargs && type.arrayDimensions() == 0)
            throw new IllegalArgumentException("Varargs parameter type must be normalized to an array");
        modifiers = ModelChecks.modifiers(modifiers);
        Objects.requireNonNull(location, "location");
    }
}
