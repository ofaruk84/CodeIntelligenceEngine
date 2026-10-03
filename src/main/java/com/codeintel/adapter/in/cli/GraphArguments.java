package com.codeintel.adapter.in.cli;

import com.codeintel.application.service.GraphViewOptions;
import com.codeintel.domain.graph.GraphTraversal;
import java.nio.file.Path;
import java.util.*;

/** Graph-only CLI options; files are always new, never overwritten. */
public record GraphArguments(GraphViewOptions selection, String format, Path output) {
    static GraphArguments parse(Map<String, String> values) {
        String direction = values.getOrDefault("--direction", "callers");
        if (!Set.of("callers", "callees").contains(direction)) throw new IllegalArgumentException("--direction requires callers or callees");
        var options = new GraphViewOptions(direction.equals("callers") ? GraphTraversal.Direction.REVERSE : GraphTraversal.Direction.FORWARD,
                number(values, "--depth", 3), number(values, "--max-nodes", 200), number(values, "--max-edges", 500));
        String format = values.getOrDefault("--format", "text");
        if (!Set.of("text", "dot", "svg", "png").contains(format)) throw new IllegalArgumentException("--format requires text, dot, svg or png");
        Path output = values.containsKey("--output") ? Path.of(values.get("--output")) : null;
        if (Set.of("svg", "png").contains(format) && output == null) throw new IllegalArgumentException("SVG/PNG requires --output <new-path>");
        return new GraphArguments(options, format, output);
    }
    private static int number(Map<String, String> values, String option, int fallback) {
        try { return Integer.parseInt(values.getOrDefault(option, Integer.toString(fallback))); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException(option + " requires an integer"); }
    }
}
