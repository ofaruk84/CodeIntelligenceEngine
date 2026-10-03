package com.codeintel.adapter.out.rendering;

import com.codeintel.application.result.GraphView;
import com.codeintel.domain.model.MethodId;
import java.util.*;

/** Pure deterministic serialization; never parses sources or traverses the graph. */
public final class GraphRenderer {
    private GraphRenderer() {}
    public static String metadata(GraphView view) {
        return "Graph: nodes=" + view.distances().size() + " edges=" + view.edges().size()
                + " truncated=" + (view.depthLimited() || view.nodeLimited() || view.edgeLimited())
                + " (depth=" + view.depthLimited() + ", nodes=" + view.nodeLimited() + ", edges=" + view.edgeLimited() + ")";
    }
    public static String text(GraphView view) {
        var keys = keys(view);
        var text = new StringBuilder(metadata(view)).append('\n');
        text.append("Selected ").append(view.options().direction() == com.codeintel.domain.graph.GraphTraversal.Direction.REVERSE ? "callers" : "callees")
                .append("; arrows always caller -> callee; depth is minimum BFS distance.\n");
        view.distances().forEach((id, depth) -> text.append(keys.get(id)).append(' ').append(label(id))
                .append(id.equals(view.target()) ? " [target]" : "")
                .append(view.externalTargets().contains(id) ? " [external]" : "")
                .append(" depth=").append(depth).append("\n  ID: ").append(line(id.value())).append('\n'));
        text.append("Edges:").append(view.edges().isEmpty() ? " (empty)" : "").append('\n');
        var seen = new HashSet<MethodId>();
        seen.add(view.target());
        for (var edge : view.edges()) {
            boolean repeat = !seen.add(edge.target());
            text.append("  ").append(keys.get(edge.caller())).append(" -> ").append(keys.get(edge.target()));
            if (edge.caller().equals(edge.target())) text.append(" [self-loop; cycle reference]");
            else if (repeat) text.append(" [repeat reference]");
            else text.append(" [reference]");
            text.append('\n');
        }
        text.append("Static resolved calls only; incomplete resolution can hide relationships.\n");
        return text.toString();
    }
    public static String dot(GraphView view) {
        var keys = keys(view);
        var dot = new StringBuilder("digraph calls {\n  rankdir=LR;\n  node [shape=box];\n  label=")
                .append(quote(metadata(view) + "\nStatic resolved calls only; resolution may be incomplete.")).append(";\n");
        view.distances().forEach((id, depth) -> {
            dot.append("  ").append(keys.get(id)).append(" [label=").append(quote(label(id)
                    + (view.externalTargets().contains(id) ? "\n[external]" : "") + "\ndepth=" + depth))
                    .append(", tooltip=").append(quote(id.value()));
            if (id.equals(view.target())) dot.append(", style=filled, fillcolor=\"#ffdf80\", penwidth=2");
            if (view.externalTargets().contains(id)) dot.append(", shape=ellipse");
            dot.append("];\n");
        });
        view.edges().forEach(edge -> dot.append("  ").append(keys.get(edge.caller())).append(" -> ")
                .append(keys.get(edge.target())).append(";\n"));
        return dot.append("}\n").toString();
    }
    private static Map<MethodId, String> keys(GraphView view) {
        var result = new LinkedHashMap<MethodId, String>();
        view.distances().keySet().forEach(id -> result.put(id, "n" + result.size()));
        return result;
    }
    private static String label(MethodId id) {
        // Full qualified owner and parameter types disambiguate overloads and nested types.
        return line(id.value());
    }
    private static String line(String value) { return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t"); }
    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
}
