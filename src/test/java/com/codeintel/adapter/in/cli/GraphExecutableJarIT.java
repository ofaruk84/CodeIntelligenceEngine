package com.codeintel.adapter.in.cli;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh-process tests analyze synthetic source without running its build or compiled code. */
class GraphExecutableJarIT {
    @TempDir Path temporary;
    private record Result(int code, String out, String err) {}
    private Result invoke(String... args) throws Exception {
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-jar",Path.of("target/code-intelligence.jar").toAbsolutePath().toString()));
        command.addAll(List.of(args));
        Path stdout = temporary.resolve("stdout.txt"), stderr = temporary.resolve("stderr.txt");
        var process = new ProcessBuilder(command).directory(temporary.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        try {
            assertTrue(process.waitFor(45,TimeUnit.SECONDS),"Graph CLI timed out");
            return new Result(process.exitValue(),Files.readString(stdout),Files.readString(stderr));
        } finally { if(process.isAlive()) { process.destroyForcibly(); process.waitFor(5,TimeUnit.SECONDS); } }
    }
    private Path fixture() throws Exception {
        Path repository = Files.createDirectory(temporary.resolve("repository with spaces"));
        Files.writeString(repository.resolve("Demo.java"),"package demo; class Demo { void entry(){ middle(); } void middle(){ end(); } void end(){ middle(); } }");
        return repository;
    }
    @Test void packagedTextDotPathsAndOverwrite() throws Exception {
        Path repository = fixture();
        var text = invoke("graph",repository.toString(),"demo.Demo#end()","--direction","callers","--depth","2");
        assertEquals(0,text.code,text.err); assertTrue(text.out.contains("entry()")); assertTrue(text.out.contains("repeat reference"));
        var dot = invoke("graph",repository.toString(),"demo.Demo#entry()","--direction","callees","--format","dot");
        assertEquals(0,dot.code,dot.err); assertTrue(dot.out.startsWith("digraph calls {")); assertFalse(dot.out.contains("Scan:"));
        assertTrue(dot.out.contains("n0 -> n1;")); assertTrue(dot.out.contains("n1 -> n2;")); assertTrue(dot.out.contains("n2 -> n1;"));
        var saved = invoke("graph",repository.toString(),"demo.Demo#entry()","--direction","callees","--format","dot","--output","export with spaces.dot");
        assertEquals(0,saved.code,saved.err); assertEquals("",saved.out);
        Path output = temporary.resolve("export with spaces.dot"); assertEquals(dot.out,Files.readString(output));
        var refused = invoke("graph",repository.toString(),"demo.Demo#entry()","--format","dot","--output",output.toString());
        assertEquals(3,refused.code); assertEquals(dot.out,Files.readString(output));
        assertEquals(4,invoke("graph",repository.toString(),"unknown").code);
        assertEquals(2,invoke("graph",repository.toString(),"demo.Demo#entry()","--depth","-1").code);
    }
    private void requireGraphviz() throws Exception {
        String dot = System.getenv("CODEINTEL_DOT");
        if(dot == null || dot.isBlank()) {
            String programFiles = System.getenv("ProgramFiles");
            Path standard = Path.of(programFiles == null ? "/nonexistent" : programFiles,"Graphviz","bin","dot.exe");
            dot = Files.isRegularFile(standard) ? standard.toString() : "dot";
        }
        Process process;
        try { process = new ProcessBuilder(dot,"-V").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start(); }
        catch(java.io.IOException failure) { Assumptions.assumeTrue(false,"Graphviz unavailable; SVG/PNG integration skipped: " + failure.getMessage()); return; }
        try { Assumptions.assumeTrue(process.waitFor(5,TimeUnit.SECONDS) && process.exitValue()==0,"Graphviz unavailable; SVG/PNG integration skipped"); }
        finally { if(process.isAlive()) process.destroyForcibly(); }
    }
    @Test void actualSvgAndPngContainExpectedDirectedGraph() throws Exception {
        requireGraphviz(); Path repository = fixture();
        var svg = invoke("graph",repository.toString(),"demo.Demo#entry()","--direction","callees","--format","svg","--output","graph with spaces.svg");
        assertEquals(0,svg.code,svg.err); assertEquals("",svg.out);
        Path output = temporary.resolve("graph with spaces.svg");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        var document = factory.newDocumentBuilder().parse(output.toFile());
        assertEquals("svg",document.getDocumentElement().getTagName());
        String content = Files.readString(output);
        assertTrue(content.contains("demo.Demo#entry()")); assertTrue(content.contains("demo.Demo#middle()"));
        var groups = document.getElementsByTagName("g"); int nodes=0,edges=0;
        for(int i=0;i<groups.getLength();i++) {
            var attribute = groups.item(i).getAttributes().getNamedItem("class");
            if (attribute == null) continue;
            String kind = attribute.getNodeValue();
            if(kind.equals("node")) nodes++; if(kind.equals("edge")) edges++;
        }
        assertEquals(3,nodes); assertEquals(3,edges);
        assertTrue(content.contains("n0&#45;&gt;n1")); assertTrue(content.contains("n2&#45;&gt;n1"));
        var png = invoke("graph",repository.toString(),"demo.Demo#entry()","--direction","callees","--format","png","--output","graph.png");
        assertEquals(0,png.code,png.err); assertEquals("",png.out);
        byte[] bytes=Files.readAllBytes(temporary.resolve("graph.png"));
        assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10},Arrays.copyOf(bytes,8));
    }
}
