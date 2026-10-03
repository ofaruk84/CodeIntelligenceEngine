package com.codeintel.domain.model;

/**
 * Semantic type input, never Java syntax to parse. Adapters supply erased resolved names
 * (including type-variable bounds), or normalized AST spelling for unresolved base types.
 * Array dimensions are separate from the base name in either case.
 */
public record TypeReference(String name, int arrayDimensions, boolean resolved) {
    public TypeReference {
        name = ModelChecks.text(name, "type name");
        if (resolved) ModelChecks.name(name);
        else if (!name.equals(name.strip()) || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Supply normalized AST type spelling");
        if (arrayDimensions < 0 || arrayDimensions > 255)
            throw new IllegalArgumentException("Array dimensions must be between 0 and 255");
        if (name.equals("void") && arrayDimensions != 0)
            throw new IllegalArgumentException("void cannot be an array");
    }

    public static TypeReference resolved(String erasedName) {
        return new TypeReference(erasedName, 0, true);
    }

    public static TypeReference unresolved(String normalizedAstSpelling) {
        return new TypeReference(normalizedAstSpelling, 0, false);
    }

    /** Adds one array dimension; use on a varargs component type exactly once. */
    public TypeReference asArray() {
        return new TypeReference(name, arrayDimensions + 1, resolved);
    }

    public String identity() {
        // Opaque unresolved spelling may contain commas and other signature punctuation.
        return (resolved ? name : "unresolved:" + name.length() + ":" + name)
                + "[]".repeat(arrayDimensions);
    }
}
