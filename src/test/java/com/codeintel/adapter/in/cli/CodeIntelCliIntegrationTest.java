package com.codeintel.adapter.in.cli;

import com.codeintel.bootstrap.EngineFactory;
import com.codeintel.adapter.out.filesystem.FileSystemRepositoryScanner;
import com.codeintel.adapter.out.javaparser.JavaParserSourceAnalyzer;
import com.codeintel.adapter.out.graph.InMemoryCodeGraph;
import com.codeintel.application.service.RepositoryAnalysisService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CodeIntelCliIntegrationTest {
    @TempDir Path root;
    private static final String PAY = fixtureId("PaymentService", "pay", 1);
    private static final String RETRY = fixtureId("RetryPaymentJob", "retry", 1);
    private static final String OVERLOADED_PAY = fixtureId("PaymentService", "pay", 2);
    private static final String AUDIT = fixtureId("PaymentService", "audit", 1);
    private static String fixtureId(String owner, String name, int parameters) {
        try {
            var fixture = Path.of(Objects.requireNonNull(CodeIntelCliIntegrationTest.class.getResource("/fixtures/commerce")).toURI());
            return EngineFactory.create().analyze(fixture).methods().keySet().stream()
                    .filter(id -> id.startsWith("com.example.commerce." + owner + "#" + name + "("))
                    .filter(id -> id.substring(id.indexOf('(') + 1, id.indexOf(')')).split(",").length == parameters)
                    .findFirst().orElseThrow();
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    private Path fixture() throws Exception { return Path.of(Objects.requireNonNull(getClass().getResource("/fixtures/commerce")).toURI()); }
    private record Result(int code, String out, String err) {}
    private Result run(CodeIntelCli cli, String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int code = cli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    private Result command(String command, String... operands) throws Exception {
        var args = new ArrayList<>(List.of(command, fixture().toString())); args.addAll(List.of(operands));
        return run(new CodeIntelCli(EngineFactory.create()), args.toArray(String[]::new));
    }
    @Test void allCommandsUseActualResolvedFixture() throws Exception {
        var scan = command("scan"); assertEquals(0, scan.code); assertTrue(scan.out.contains("scanned=13"));
        assertTrue(scan.err.contains("Snapshot-wide UNRESOLVED"));
        assertTrue(command("search", "#pay(").out.contains(PAY));
        assertTrue(command("method", PAY).out.contains("METHOD " + PAY));
        assertTrue(command("callers", PAY).out.contains(OVERLOADED_PAY));
        assertTrue(command("callees", RETRY).out.contains(OVERLOADED_PAY));
        assertTrue(command("dependencies", RETRY).out.contains(PAY + " depth=2"));
        assertTrue(command("path", RETRY, PAY).out.contains("Path: " + RETRY));
        var impact = command("impact", PAY); assertEquals(0, impact.code);
        assertTrue(impact.out.contains(RETRY)); assertTrue(impact.out.contains("not proven"));
        assertEquals(scan, command("scan"));
    }
    @Test void unknownDisconnectedEmptyAndExternalAreDifferent() throws Exception {
        assertEquals(4, command("method", "missing").code);
        assertEquals(4, command("path", PAY, "missing").code);
        assertTrue(command("search", "nothing-matches-this").out.contains("No matching symbols"));
        var disconnected = command("path", PAY, "com.example.commerce.CycleA#run()");
        assertEquals(0, disconnected.code); assertTrue(disconnected.out.contains("No path between known"));
        assertTrue(command("path", PAY, PAY).out.contains("Path: " + PAY));
        assertTrue(command("callees", AUDIT).out.contains("(empty)"));
        assertTrue(command("callees", "com.example.commerce.CycleA#run()").out.contains("CycleB#run()"));
        var snapshot = EngineFactory.create().analyze(fixture());
        for (var id : snapshot.coverage().externalTargets()) {
            var result = command("method", id.value()); assertEquals(0, result.code); assertTrue(result.out.contains("Known external target"));
        }
        for (var id : snapshot.methods().keySet()) {
            if (id.contains("<init>") || id.contains("$")) assertEquals(0, command("method", id).code);
        }
        assertEquals(0, command("dependencies", "com.example.commerce.CycleA#run()").code);
    }
    @Test void analyzesExactlyOnceAndSyntaxNeverAnalyzes() throws Exception {
        var scans = new AtomicInteger(); var parses = new AtomicInteger(); var graphs = new AtomicInteger();
        var cli = new CodeIntelCli(new RepositoryAnalysisService((path, roots) -> {
            scans.incrementAndGet(); return new FileSystemRepositoryScanner().scan(path, roots);
        }, sources -> { parses.incrementAndGet(); return new JavaParserSourceAnalyzer().analyze(sources); },
                units -> { graphs.incrementAndGet(); return InMemoryCodeGraph.fromSources(units); }));
        assertEquals(0, run(cli, "--help").code);
        assertTrue(run(cli).out.contains("Usage:"));
        for (String[] args : List.of(new String[]{"bogus"},
                new String[]{"scan"}, new String[]{"scan", ".", "extra"}, new String[]{"scan", ".", "--source-root"},
                new String[]{"scan", ".", "--unknown"}, new String[]{"method", ".", ""}))
            assertEquals(2, run(cli, args).code);
        assertEquals(0, scans.get());
        for (String command : List.of("scan", "search", "method", "callers", "callees", "dependencies", "path", "impact")) {
            var args = new ArrayList<>(List.of(command, fixture().toString()));
            if (!command.equals("scan")) args.add(command.equals("search") ? "pay" : PAY);
            if (command.equals("path")) args.add(PAY);
            assertEquals(0, run(cli, args.toArray(String[]::new)).code);
        }
        assertEquals(8, scans.get()); assertEquals(8, parses.get()); assertEquals(8, graphs.get());
    }
    @Test void partialAnalysisRootsAndInvalidInputs() throws Exception {
        Files.createDirectories(root.resolve("custom/example"));
        Files.writeString(root.resolve("custom/example/Good.java"), "package example; class Good { void target() {} void caller() { target(); missing(); } }");
        Files.writeString(root.resolve("Broken.java"), "class Broken { void bad( }");
        var cli = new CodeIntelCli(EngineFactory.create());
        var result = run(cli, "--source-root", "custom", "impact", root.toString(), "example.Good#target()", "--source-root", root.resolve("custom").toString());
        assertEquals(5, result.code); assertTrue(result.out.contains("failed=1"));
        assertTrue(result.out.contains("example.Good#caller()")); assertTrue(result.err.contains("PARSE_ERROR"));
        assertTrue(result.err.contains("missing()")); assertTrue(result.out.contains("Source roots: [custom]"));
        assertEquals(3, run(cli, "scan", root.resolve("absent").toString()).code);
        assertEquals(3, run(cli, "scan", root.resolve("Broken.java").toString()).code);
        assertEquals(3, run(cli, "scan", root.toString(), "--source-root", "../outside").code);
        assertEquals(2, run(cli, "scan", root.toString(), "extra").code);
        assertEquals(5, run(cli, "search", root.toString(), "--", "--query").code);
        var fatal = new CodeIntelCli(new RepositoryAnalysisService((path, roots) -> { throw new IllegalStateException("synthetic failure"); },
                sources -> List.of(), InMemoryCodeGraph::fromSources));
        var failure = run(fatal, "scan", root.toString()); assertEquals(1, failure.code);
        assertTrue(failure.err.contains("synthetic failure")); assertFalse(failure.err.contains("at com."));
    }
}
