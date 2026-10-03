package com.codeintel.domain.model;

/** Repository-relative inclusive range, with one-based line and column coordinates. */
public record SourceLocation(String path, int startLine, int startColumn, int endLine, int endColumn) {
    public SourceLocation {
        path = ModelChecks.relativePath(path);
        if (startLine < 1 || startColumn < 1 || endLine < startLine || endColumn < 1
                || (endLine == startLine && endColumn < startColumn))
            throw new IllegalArgumentException("Invalid source range");
    }

    /** Length-prefixed path keeps path punctuation distinct from range delimiters. */
    public String discriminator() {
        return path.length() + ":" + path + ":" + startLine + ":" + startColumn + "-" + endLine + ":" + endColumn;
    }
}
