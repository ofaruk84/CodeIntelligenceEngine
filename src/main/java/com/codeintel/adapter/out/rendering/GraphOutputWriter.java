package com.codeintel.adapter.out.rendering;

import com.codeintel.application.result.GraphView;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Publishes complete output to a new file only; failures preserve every existing destination. */
public final class GraphOutputWriter {
    private final GraphvizProcess graphviz;
    public GraphOutputWriter() { this(new GraphvizProcess()); }
    public GraphOutputWriter(GraphvizProcess graphviz) { this.graphviz = java.util.Objects.requireNonNull(graphviz); }
    public void write(GraphView view, String format, Path destination) throws IOException {
        Path output = destination.toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException("Output exists; overwrite refused: " + output);
        Path parent = output.getParent();
        if (!Files.isDirectory(parent)) throw new IOException("Output parent directory does not exist: " + parent);
        Path staged = Files.createTempFile(parent, ".codeintel-graph-", ".tmp");
        Path dot = null;
        try {
            switch (format) {
                case "text" -> Files.writeString(staged, GraphRenderer.text(view), StandardCharsets.UTF_8);
                case "dot" -> Files.writeString(staged, GraphRenderer.dot(view), StandardCharsets.UTF_8);
                case "svg", "png" -> {
                    dot = Files.createTempFile(parent, ".codeintel-graph-", ".dot");
                    Files.writeString(dot, GraphRenderer.dot(view), StandardCharsets.UTF_8);
                    graphviz.render(dot, staged, format);
                }
                default -> throw new IllegalArgumentException("Unsupported graph format: " + format);
            }
            // No REPLACE_EXISTING, including if another invocation creates the path meanwhile.
            Files.move(staged, output);
        } finally {
            Files.deleteIfExists(staged);
            if (dot != null) Files.deleteIfExists(dot);
        }
    }
}
