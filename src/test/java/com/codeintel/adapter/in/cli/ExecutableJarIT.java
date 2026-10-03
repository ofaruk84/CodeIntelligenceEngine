package com.codeintel.adapter.in.cli;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Runs after package through Failsafe, without a test or IDE classpath. */
class ExecutableJarIT {
    @TempDir Path temporary;
    @Test void shadedJarRunsInFreshProcesses() throws Exception {
        var fixture = Path.of(Objects.requireNonNull(getClass().getResource("/fixtures/commerce")).toURI());
        invoke(0, "--help");
        invoke(0, "scan", fixture.toString());
        invoke(0, "impact", fixture.toString(), "com.example.commerce.CycleA#run()");
        invoke(2, "invalid");
        invoke(3, "scan", temporary.resolve("missing").toString());
        invoke(4, "method", fixture.toString(), "unknown");
        Files.writeString(temporary.resolve("Broken.java"), "class Broken { void bad( }");
        invoke(5, "scan", temporary.toString());
    }
    private void invoke(int expected, String... args) throws Exception {
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", Path.of("target/code-intelligence.jar").toAbsolutePath().toString()));
        command.addAll(List.of(args));
        var stdout = temporary.resolve("stdout.txt"); var stderr = temporary.resolve("stderr.txt");
        var process = new ProcessBuilder(command).directory(temporary.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Packaged CLI timed out");
            String out = Files.readString(stdout); String err = Files.readString(stderr);
            assertEquals(expected, process.exitValue(), () -> out + "\n" + err);
            if (args[0].equals("scan") && expected == 0) {
                assertTrue(out.contains("scanned=13")); assertTrue(err.contains("Snapshot-wide UNRESOLVED")); assertFalse(err.contains("INFO:"));
            }
            if (args[0].equals("impact")) assertTrue(out.contains("CycleC#run()"));
            if (expected == 2) assertTrue(err.contains("Usage error"));
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); } }
    }
}
