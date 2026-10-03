package com.codeintel.adapter.out.rendering;

import com.codeintel.application.result.GraphView;
import com.codeintel.application.service.GraphViewOptions;
import com.codeintel.domain.graph.*;
import com.codeintel.domain.model.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GraphRenderingTest {
    @TempDir Path directory;
    private GraphView view() {
        var location = new SourceLocation("folder/quote\"\\name.java", 1, 1, 1, 10);
        var a = new MethodId(SymbolId.canonical("example.Outer$Inner"), "<init>", List.of(TypeReference.unresolved("Missing")), Optional.of(location));
        var b = MethodId.canonical(SymbolId.canonical("example.Other"), "run", List.of(TypeReference.resolved("java.lang.String")));
        var distances = new LinkedHashMap<MethodId,Integer>(); distances.put(a,0); distances.put(b,1);
        return new GraphView(a, GraphViewOptions.defaults(), distances, List.of(new GraphEdge(a,a), new GraphEdge(a,b), new GraphEdge(b,a)), Set.of(b), false,false,false);
    }
    @Test void deterministicDotEscapesLabelsAndKeepsStableKeysAndArrows() {
        var dot = GraphRenderer.dot(view());
        assertEquals(dot, GraphRenderer.dot(view()));
        assertTrue(dot.contains("n0 -> n0;")); assertTrue(dot.contains("n0 -> n1;")); assertTrue(dot.contains("n1 -> n0;"));
        assertTrue(dot.contains("fillcolor")); assertTrue(dot.contains("[external]"));
        assertTrue(dot.contains("quote\\\"/name.java"));
        assertEquals("\"a\\\"\\\\\\n\\r\\t\"", GraphRenderer.quote("a\"\\\n\r\t"));
        var text = GraphRenderer.text(view());
        assertTrue(text.contains("[target]")); assertTrue(text.contains("[external]"));
        assertTrue(text.contains("self-loop; cycle reference")); assertTrue(text.contains("repeat reference"));
        assertTrue(text.contains("ID: " + view().target().value()));
    }
    @Test void newFilesSpacesRelativePathsAndFailurePreserveExistingOutput() throws Exception {
        var folder = Files.createDirectory(directory.resolve("space folder"));
        var file = folder.resolve("graph.dot");
        var writer = new GraphOutputWriter(); writer.write(view(),"dot",file);
        assertEquals(GraphRenderer.dot(view()),Files.readString(file));
        assertThrows(FileAlreadyExistsException.class, () -> writer.write(view(),"text",file));
        assertEquals(GraphRenderer.dot(view()),Files.readString(file));
        assertThrows(java.io.IOException.class, () -> writer.write(view(),"text",folder.resolve("missing/graph.txt")));
        var broken = new GraphOutputWriter(new GraphvizProcess(directory.resolve("missing-dot").toString(),Duration.ofMillis(100)));
        var failed = folder.resolve("failed.svg");
        var failure = assertThrows(java.io.IOException.class, () -> broken.write(view(),"svg",failed));
        assertTrue(failure.getMessage().contains("CODEINTEL_DOT")); assertFalse(Files.exists(failed));
        try (var entries = Files.list(folder)) { assertEquals(List.of(file), entries.toList()); }
        Path relative = Path.of("").toAbsolutePath().relativize(folder.resolve("relative.txt"));
        writer.write(view(),"text",relative); assertTrue(Files.readString(relative).startsWith("Graph:"));
    }
    @Test void externalProcessNonzeroTimeoutAndLargeStderrHaveNoDeadlock() throws Exception {
        // Use the installed Java executable as a synthetic renderer process, never repository code.
        Path java = Path.of(System.getProperty("java.home"),"bin","java");
        Path source = directory.resolve("Fail.java");
        Files.writeString(source, "class Fail { public static void main(String[] a) throws Exception { for(int i=0;i<20000;i++) System.err.println(\"synthetic failure\"); System.exit(7); } }");
        // Graphviz-specific argument contract is tested with a task-owned launcher script.
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        org.junit.jupiter.api.Assumptions.assumeTrue(windows, "Synthetic CMD renderer failure/timeout simulation requires Windows");
        if (windows) {
            Path script = directory.resolve("fail.cmd");
            Files.writeString(script,"@echo off\r\n\""+java+"\" \""+source+"\"\r\nexit /b %errorlevel%\r\n");
            var writer = new GraphOutputWriter(new GraphvizProcess(script.toString(),Duration.ofSeconds(15)));
            var failure = assertThrows(java.io.IOException.class, () -> writer.write(view(),"svg",directory.resolve("failed.svg")));
            assertTrue(failure.getMessage().contains("exited 7"), failure.getMessage()); assertTrue(failure.getMessage().contains("synthetic failure"), failure.getMessage());
            Files.writeString(script,"@echo off\r\n\""+java+"\" \""+directory.resolve("Sleep.java")+"\"\r\n");
            Files.writeString(directory.resolve("Sleep.java"),"class Sleep { public static void main(String[] a) throws Exception { Thread.sleep(5000); } }");
            var timeout = new GraphOutputWriter(new GraphvizProcess(script.toString(),Duration.ofMillis(50)));
            var timedOut = assertThrows(java.io.IOException.class, () -> timeout.write(view(),"svg",directory.resolve("timeout.svg")));
            assertTrue(timedOut.getMessage().contains("timed out"), timedOut.getMessage());
            assertFalse(Files.exists(directory.resolve("timeout.svg")));
        }
    }
}
