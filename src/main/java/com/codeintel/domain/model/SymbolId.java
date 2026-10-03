package com.codeintel.domain.model;

import java.util.Objects;
import java.util.Optional;

/** A type identity. Member types use binary-style names, for example example.Outer$Inner. */
public record SymbolId(String qualifiedName, Optional<SourceLocation> declaration) {
    public SymbolId {
        qualifiedName = ModelChecks.name(qualifiedName);
        Objects.requireNonNull(declaration, "declaration");
    }

    public static SymbolId canonical(String qualifiedName) {
        return new SymbolId(qualifiedName, Optional.empty());
    }

    /** Required for local/anonymous types and for each conflicting canonical declaration. */
    public SymbolId at(SourceLocation location) {
        return new SymbolId(qualifiedName, Optional.of(location));
    }

    public String value() {
        return qualifiedName + declaration.map(l -> "@" + l.discriminator()).orElse("");
    }

    @Override public String toString() { return value(); }
}
