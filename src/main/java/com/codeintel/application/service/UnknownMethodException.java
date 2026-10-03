package com.codeintel.application.service;

import java.util.List;

/** Explicit unknown graph IDs; source-definition absence is handled separately by getMethod. */
public final class UnknownMethodException extends IllegalArgumentException {
    private final List<String> ids;
    public UnknownMethodException(List<String> ids) {
        super("Unknown graph IDs: " + String.join(", ", ids));
        this.ids = List.copyOf(ids);
    }
    public List<String> ids() { return ids; }
}
