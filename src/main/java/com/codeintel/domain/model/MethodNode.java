package com.codeintel.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Callable definition. Calls are stored once at source-unit level, not as mutable edges here. */
public record MethodNode(MethodId id, CallableKind kind, Optional<TypeReference> returnType,
                         List<ParameterInfo> parameters, Set<String> modifiers,
                         List<TypeReference> thrownTypes, SourceLocation location) {
    public MethodNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(returnType, "returnType");
        parameters = List.copyOf(parameters);
        modifiers = ModelChecks.modifiers(modifiers);
        thrownTypes = List.copyOf(thrownTypes);
        Objects.requireNonNull(location, "location");
        boolean constructor = kind == CallableKind.CONSTRUCTOR;
        if (constructor != id.name().equals("<init>"))
            throw new IllegalArgumentException("Callable kind and identity disagree");
        if (constructor == returnType.isPresent())
            throw new IllegalArgumentException("Only methods have a return type (including void)");
        if (!id.parameterTypes().equals(parameters.stream().map(ParameterInfo::type).toList()))
            throw new IllegalArgumentException("Identity and ordered parameter types disagree");
        if (id.declaration().isPresent() && !id.declaration().get().equals(location))
            throw new IllegalArgumentException("Identity qualifier must match declaration location");
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).varargs() && i != parameters.size() - 1)
                throw new IllegalArgumentException("Only the last parameter may be varargs");
        }
        if (thrownTypes.stream().anyMatch(t -> t.name().equals("void")))
            throw new IllegalArgumentException("Thrown type cannot be void");
    }
}
