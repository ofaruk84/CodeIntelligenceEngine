package com.codeintel.adapter.in.cli;

import com.codeintel.adapter.out.filesystem.FileSystemRepositoryScanner;
import com.codeintel.adapter.out.javaparser.JavaParserSourceAnalyzer;
import com.codeintel.adapter.out.graph.InMemoryCodeGraph;
import com.codeintel.application.service.RepositoryAnalysisService;
import com.codeintel.bootstrap.EngineFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GraphCommandTest {
    @TempDir Path root;
    private record Result(int code, String out, String err) {}
    private Result run(CodeIntelCli cli, String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int code = cli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));
        return new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private void fixture() throws Exception {
        Files.writeString(root.resolve("Sample.java"), """
            package example;
            class Sample {
                void target() { target(); shared(); }
                void target(int value) { target(); }
                void caller() { target(); shared(); }
                void shared() {}
                void isolated() {}
                void external() { String.valueOf(1); }
                void incomplete(Missing value) { missing(); }
                class Inner { Inner() { target(); } }
            }
            """);
    }
    @Test void oneAnalysisDotStdoutAndParsedIdentities() throws Exception {
        fixture();
        var scans = new AtomicInteger(); var parses = new AtomicInteger(); var graphs = new AtomicInteger();
        var cli = new CodeIntelCli(new RepositoryAnalysisService((p,r) -> { scans.incrementAndGet(); return new FileSystemRepositoryScanner().scan(p,r); },
                s -> { parses.incrementAndGet(); return new JavaParserSourceAnalyzer().analyze(s); },
                s -> { graphs.incrementAndGet(); return InMemoryCodeGraph.fromSources(s); }));
        var result = run(cli,"graph",root.toString(),"example.Sample#target()","--format","dot");
        assertEquals(0,result.code); assertTrue(result.out.startsWith("digraph calls {"));
        assertFalse(result.out.contains("Scan:")); assertTrue(result.err.contains("Scan:"));
        assertTrue(result.err.contains("Coverage incomplete")); assertTrue(result.out.contains("target(int)"));
        assertTrue(result.out.contains("Sample$Inner#<init>()"));
        assertEquals(1,scans.get()); assertEquals(1,parses.get()); assertEquals(1,graphs.get());
        var snapshot = EngineFactory.create().analyze(root);
        String fallback = snapshot.methods().keySet().stream().filter(id -> id.contains("#incomplete(")).findFirst().orElseThrow();
        var fallbackResult = run(cli,"graph",root.toString(),fallback);
        assertTrue(fallbackResult.out.contains(fallback)); assertTrue(fallbackResult.out.contains("Edges: (empty)"));
        var isolated = run(cli,"graph",root.toString(),"example.Sample#isolated()");
        assertEquals(0,isolated.code); assertTrue(isolated.out.contains("nodes=1 edges=0"));
        assertFalse(isolated.out.contains("[external]"));
        assertFalse(snapshot.coverage().externalTargets().isEmpty());
        for (var external : snapshot.coverage().externalTargets()) {
            var externalResult = run(cli,"graph",root.toString(),external.value());
            assertEquals(0,externalResult.code); assertTrue(externalResult.out.contains("[target] [external]"));
        }
        assertEquals(4,run(cli,"graph",root.toString(),"unknown").code);
    }
    @Test void optionsErrorsAndFileOutputNeverContaminateStdout() throws Exception {
        fixture(); var cli = new CodeIntelCli(EngineFactory.create());
        for (String[] options : new String[][]{{"--direction","up"},{"--depth","-1"},{"--depth","101"},{"--depth","bad"},
                {"--max-nodes","0"},{"--max-edges","50001"},{"--format","pdf"},{"--format","svg"},
                {"--format","png"},{"--depth"},{"--depth","1","--depth","2"}}) {
            var args = new java.util.ArrayList<>(java.util.List.of("graph",root.toString(),"example.Sample#target()"));
            args.addAll(java.util.List.of(options)); assertEquals(2,run(cli,args.toArray(String[]::new)).code);
        }
        assertEquals(2,run(cli,"scan",root.toString(),"--format","dot").code);
        Path output = root.resolve("graph with spaces.dot");
        var saved = run(cli,"graph",root.toString(),"example.Sample#target()","--output",output.toString(),"--format","dot");
        assertEquals(0,saved.code); assertEquals("",saved.out); assertTrue(saved.err.contains("Graph saved:"));
        String previous = Files.readString(output);
        assertEquals(3,run(cli,"graph",root.toString(),"example.Sample#target()","--output",output.toString()).code);
        assertEquals(previous,Files.readString(output));
        assertEquals(3,run(cli,"graph",root.toString(),"example.Sample#target()","--output",root.resolve("absent/graph.txt").toString()).code);
        var zero = run(cli,"graph",root.toString(),"example.Sample#target()","--direction","callees","--depth","0");
        assertTrue(zero.out.contains("nodes=1 edges=1 truncated=true")); assertTrue(zero.out.contains("self-loop"));
    }
}
