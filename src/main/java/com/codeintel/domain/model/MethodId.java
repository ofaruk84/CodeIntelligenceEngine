package com.codeintel.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Overload-safe callable identity; return type and parameter names are deliberately excluded. */
public record MethodId(SymbolId declaringType, String name, List<TypeReference> parameterTypes,
                       Optional<SourceLocation> declaration) {
    public MethodId {
        Objects.requireNonNull(declaringType, "declaringType");
        if (!"<init>".equals(name)) ModelChecks.simpleName(name);
        parameterTypes = List.copyOf(parameterTypes);
        Objects.requireNonNull(declaration, "declaration");
        if (parameterTypes.stream().anyMatch(t -> t.name().equals("void")))
            throw new IllegalArgumentException("A parameter cannot be void");
        if (parameterTypes.stream().anyMatch(t -> !t.resolved()) && declaration.isEmpty())
            throw new IllegalArgumentException("Unresolved parameters require a declaration location");
    }

    public static MethodId canonical(SymbolId owner, String name, List<TypeReference> parameters) {
        return new MethodId(owner, name, parameters, Optional.empty());
    }

    /** Separates duplicate declarations without replacing the canonical name components. */
    public MethodId at(SourceLocation location) {
        return new MethodId(declaringType, name, parameterTypes, Optional.of(location));
    }

    public String value() {
        return declaringType.value() + "#" + name + "(" + parameterTypes.stream()
                .map(TypeReference::identity).collect(Collectors.joining(",")) + ")"
                + declaration.map(l -> "@" + l.discriminator()).orElse("");
    }

    @Override public String toString() { return value(); }
}
