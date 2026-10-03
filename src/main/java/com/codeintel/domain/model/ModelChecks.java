package com.codeintel.domain.model;

import java.util.Objects;
import java.util.Set;

/** Shared value validation, not a Java syntax parser. */
final class ModelChecks {
    private ModelChecks() {}

    static String text(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    static String name(String value) {
        text(value, "name");
        boolean start = true;
        for (int cp : value.codePoints().toArray()) {
            if (cp == '.') {
                if (start) throw new IllegalArgumentException("Empty name segment");
                start = true;
            } else {
                if (!(start ? Character.isJavaIdentifierStart(cp) : Character.isJavaIdentifierPart(cp))
                        || Character.isIdentifierIgnorable(cp))
                    throw new IllegalArgumentException("Invalid semantic name: " + value);
                start = false;
            }
        }
        if (start) throw new IllegalArgumentException("Empty name segment");
        return value;
    }

    static String simpleName(String value) {
        name(value);
        if (value.contains(".")) throw new IllegalArgumentException("Expected a simple name");
        return value;
    }

    static String relativePath(String value) {
        String path = text(value, "path").replace('\\', '/');
        if (path.startsWith("/") || path.contains(":"))
            throw new IllegalArgumentException("Path must be repository-relative");
        for (String segment : path.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals(".."))
                throw new IllegalArgumentException("Path must not contain empty, dot, or parent segments");
        }
        if (path.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Path must not contain control characters");
        return path;
    }

    static Set<String> modifiers(Set<String> values) {
        Set<String> copy = Set.copyOf(values);
        for (String value : copy) {
            text(value, "modifier");
            if (!value.equals(value.strip())) throw new IllegalArgumentException("Modifier must be normalized");
        }
        return copy;
    }
}
