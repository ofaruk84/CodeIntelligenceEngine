package com.codeintel.adapter.out.rendering;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** External renderer with argument-array execution and file-based stderr capture (no pipe deadlock). */
public final class GraphvizProcess {
    private final String executable;
    private final Duration timeout;
    public GraphvizProcess() { this(executable(), Duration.ofSeconds(30)); }
    public GraphvizProcess(String executable, Duration timeout) {
        if (executable == null || executable.isBlank() || timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Graphviz executable and positive timeout required");
        this.executable = executable; this.timeout = timeout;
    }
    private static String executable() {
        String configured = System.getenv("CODEINTEL_DOT");
        if (configured != null && !configured.isBlank()) return configured;
        // Winget's standard Windows installation can be used before a terminal PATH refresh.
        if (System.getProperty("os.name").startsWith("Windows")) {
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                Path installed = Path.of(programFiles, "Graphviz", "bin", "dot.exe");
                if (Files.isRegularFile(installed)) return installed.toString();
            }
        }
        return "dot";
    }
    public void render(Path dot, Path output, String format) throws IOException {
        if (!Set.of("svg", "png").contains(format)) throw new IllegalArgumentException("Unsupported rendered format");
        Path errors = Files.createTempFile(output.getParent(), ".codeintel-stderr-", ".txt");
        Process process = null;
        try {
            try {
                process = new ProcessBuilder(executable, "-T" + format, dot.toString(), "-o", output.toString())
                        .redirectError(errors.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException failure) {
                throw new IOException("Cannot start Graphviz. Install Graphviz, put dot on PATH or set CODEINTEL_DOT to its executable path; text/DOT need no Graphviz.", failure);
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IOException("Graphviz timed out after " + timeout.toSeconds() + " seconds");
            if (process.exitValue() != 0) {
                String message;
                try (var input = Files.newInputStream(errors)) {
                    byte[] bytes = input.readNBytes(8192);
                    message = bytes.length == 0 ? "(no stderr)" : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                }
                throw new IOException("Graphviz exited " + process.exitValue() + ": " + message);
            }
            if (!Files.isRegularFile(output) || Files.size(output) == 0) throw new IOException("Graphviz produced no output");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new IOException("Graphviz rendering interrupted", failure);
        } finally {
            if (process != null && process.isAlive()) {
                var descendants = process.descendants().toList();
                descendants.forEach(child -> child.destroyForcibly());
                process.destroyForcibly();
                try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                for (var child : descendants) {
                    try { child.onExit().get(1, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) { /* Already requested termination. */ }
                }
            }
            Files.deleteIfExists(errors);
        }
    }
}
