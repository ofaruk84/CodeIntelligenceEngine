package com.codeintel.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A type definition with ID links to its enclosing type, never a mutable parent/child graph. */
public record ClassNode(SymbolId id, Optional<String> simpleName, TypeKind kind, Nesting nesting,
                        Optional<SymbolId> enclosingType, Set<String> modifiers,
                        List<TypeReference> supertypes, List<FieldInfo> fields,
                        List<MethodNode> methods, SourceLocation location) {
    /** Nesting is independent of class/interface/enum/record/annotation kind. */
    public enum Nesting { TOP_LEVEL, MEMBER, LOCAL, ANONYMOUS }

    public ClassNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(simpleName, "simpleName");
        simpleName.ifPresent(ModelChecks::simpleName);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(nesting, "nesting");
        Objects.requireNonNull(enclosingType, "enclosingType");
        modifiers = ModelChecks.modifiers(modifiers);
        supertypes = List.copyOf(supertypes);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
        Objects.requireNonNull(location, "location");
        if ((nesting == Nesting.TOP_LEVEL) == enclosingType.isPresent())
            throw new IllegalArgumentException("Only nested types have an enclosing type");
        if ((nesting == Nesting.ANONYMOUS) == simpleName.isPresent())
            throw new IllegalArgumentException("Only anonymous types have no simple name");
        if (nesting == Nesting.ANONYMOUS && kind != TypeKind.CLASS)
            throw new IllegalArgumentException("Anonymous types must be classes");
        if ((nesting == Nesting.LOCAL || nesting == Nesting.ANONYMOUS) && id.declaration().isEmpty())
            throw new IllegalArgumentException("Local and anonymous types require location-qualified identities");
        if (id.declaration().isPresent() && !id.declaration().get().equals(location))
            throw new IllegalArgumentException("Type qualifier must match declaration location");
        if (enclosingType.filter(id::equals).isPresent())
            throw new IllegalArgumentException("A type cannot enclose itself");
        if (nesting == Nesting.MEMBER && !id.qualifiedName().equals(
                enclosingType.orElseThrow().qualifiedName() + "$" + simpleName.orElseThrow()))
            throw new IllegalArgumentException("Member type identity must use enclosing name and $");
        if (nesting == Nesting.MEMBER && enclosingType.orElseThrow().declaration().isPresent()
                && id.declaration().isEmpty())
            throw new IllegalArgumentException("Members of location-qualified types also require location qualifiers");
        for (MethodNode method : methods) {
            if (!method.id().declaringType().equals(id))
                throw new IllegalArgumentException("Method belongs to a different type");
        }
    }
}
