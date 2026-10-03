package com.codeintel.adapter.in.cli;

import com.codeintel.bootstrap.EngineFactory;
import com.codeintel.application.service.*;
import com.codeintel.domain.model.ResolutionStatus;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsAndClasspathTest {
    @TempDir Path temporary;
    private record Result(int code, String out, String err) {}
    private Result run(String... args) {
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int code = new CodeIntelCli(EngineFactory.create()).run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
    @Test void hundredsOfFailuresHaveBoundedPresentationAndCompleteEvidence() throws Exception {
        Path repository = Files.createDirectory(temporary.resolve("repository"));
        Files.writeString(repository.resolve("Example.java"),
                "class Example { void run() {" + "missing();\n".repeat(600) + "} }");
        var normal = run("scan", repository.toString());
        assertEquals(0, normal.code()); assertTrue(normal.out().contains("unresolved=600"));
        assertTrue(normal.err().lines().count() < 20); assertTrue(normal.err().length() < 3000);
        assertFalse(normal.err().contains("Source WARNING UNSOLVED_SYMBOL"));
        var detailed = run("scan", repository.toString(), "--detailed-diagnostics");
        assertEquals(normal.out(), detailed.out());
        assertEquals(600, detailed.err().lines().filter(line -> line.contains("expression=missing()")).count());
        Path file = temporary.resolve("evidence.jsonl");
        assertEquals(0, run("search", repository.toString(), "Example", "--diagnostics-file", file.toString()).code());
        var records = Files.readAllLines(file);
        assertEquals(600, records.stream().filter(line -> line.contains("\"kind\":\"call\"")).count());
        assertEquals(600, records.stream().filter(line -> line.contains("\"kind\":\"sourceDiagnostic\"")).count());
        assertTrue(records.getFirst().contains("\"schemaVersion\":1"));
        var snapshot = EngineFactory.create().analyze(repository);
        assertEquals(600, snapshot.coverage().unresolvedCalls().size());
        assertEquals(600, new CodeIntelligenceService(snapshot).analyzeChangeImpact("Example#run()")
                .coverage().unresolvedCalls().size());
        String original = Files.readString(file);
        assertEquals(3, run("scan", repository.toString(), "--diagnostics-file", file.toString()).code());
        assertEquals(original, Files.readString(file));
        assertEquals(3, run("scan", repository.toString(), "--diagnostics-file", temporary.resolve("absent/out.jsonl").toString()).code());
        assertEquals(2, run("scan", repository.toString(), "--dependency-jar").code());
        assertEquals(2, run("scan", repository.toString(), "--diagnostics-file", "a", "--diagnostics-file", "b").code());
    }
    private Path dependency(String filename, String source) throws Exception {
        Path directory = Files.createDirectory(temporary.resolve(filename + "-build"));
        Path java = directory.resolve(source.contains("@interface Data") ? "Data.java" : "Api.java"); Files.writeString(java, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "21", "-proc:none", "-d", directory.toString(), java.toString()));
        Path jar = temporary.resolve(filename);
        try (var output = new JarOutputStream(Files.newOutputStream(jar)); var files = Files.walk(directory)) {
            for (var file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                output.putNextEntry(new JarEntry(directory.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output); output.closeEntry();
            }
        }
        return jar;
    }
    @Test void explicitJarResolvesOverloadsWithoutChangingSourceIdentities() throws Exception {
        Path jar = dependency("local library.jar", "package dependency; public class Api { public static int call(int n) { return n; } public static int call(long n) { return 0; } public static class Nested { public static void work() {} } }");
        Path repository = Files.createDirectory(temporary.resolve("source"));
        Files.writeString(repository.resolve("Client.java"), "class Client { void run() { dependency.Api.call(1); dependency.Api.call(1L); dependency.Api.Nested.work(); } }");
        var engine = EngineFactory.create();
        var without = engine.analyze(repository);
        assertEquals(3, without.coverage().unresolvedCalls().size());
        var options = new AnalysisOptions(List.of(), List.of(jar, jar));
        var with = engine.analyze(repository, options);
        assertEquals(without.methods().keySet(), with.methods().keySet());
        assertEquals(3, with.coverage().resolvedCallCount());
        assertEquals(Set.of("dependency.Api#call(int)", "dependency.Api#call(long)", "dependency.Api$Nested#work()"),
                new HashSet<>(with.coverage().externalTargets().stream().map(id -> id.value()).toList()));
        assertTrue(with.sources().getFirst().calls().stream().allMatch(call -> call.status() == ResolutionStatus.RESOLVED));
        assertEquals(0, run("scan", repository.toString(), "--dependency-jar", jar.toString()).code());
        assertEquals(with.coverage(), engine.analyze(repository, options).coverage());
        // A completed invocation must not leave owned file handles locking the supplied archive.
        Files.move(jar, temporary.resolve("moved.jar"));
        assertEquals(3, run("scan", repository.toString(), "--dependency-jar", jar.toString()).code());
        Path bad = temporary.resolve("bad.jar"); Files.writeString(bad, "not a ZIP archive");
        assertEquals(3, run("scan", repository.toString(), "--dependency-jar", bad.toString()).code());
        assertEquals(3, run("scan", repository.toString(), "--dependency-jar", repository.toString()).code());
        Path malformed = temporary.resolve("malformed.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(malformed))) {
            output.putNextEntry(new JarEntry("dependency/Bad.class")); output.write(new byte[]{1, 2, 3}); output.closeEntry();
        }
        assertEquals(3, run("scan", repository.toString(), "--dependency-jar", malformed.toString()).code());
    }
    @Test void sourceDefinitionsWinAndEngineClasspathStaysIsolated() throws Exception {
        Path jar = dependency("shadow.jar", "package dependency; public class Api { public static void call(int n) {} }");
        Path repository = Files.createDirectory(temporary.resolve("project"));
        Files.createDirectories(repository.resolve("dependency"));
        Files.writeString(repository.resolve("dependency/Api.java"), "package dependency; public class Api { public static void call(int n) {} }");
        Files.writeString(repository.resolve("Client.java"), "class Client { void run() { dependency.Api.call(1); com.github.javaparser.StaticJavaParser.parse(\"class Hidden {}\"); } }");
        var snapshot = EngineFactory.create().analyze(repository, new AnalysisOptions(List.of(), List.of(jar)));
        assertTrue(snapshot.methods().containsKey("dependency.Api#call(int)"));
        assertFalse(snapshot.coverage().externalTargets().stream().anyMatch(id -> id.value().equals("dependency.Api#call(int)")));
        assertEquals(1, snapshot.coverage().unresolvedCalls().size());
        assertTrue(snapshot.coverage().unresolvedCalls().getFirst().rawExpression().contains("StaticJavaParser"));
        Path relative = Path.of("").toAbsolutePath().relativize(jar);
        assertEquals(0, run("scan", repository.toString(), "--dependency-jar", relative.toString(),
                "--dependency-jar", jar.toString()).code());
    }
    @Test void explicitJarOrderingIsDeterministicAndGeneratedAccessorsStayUnresolved() throws Exception {
        Path first = dependency("first.jar", "package dependency; public class Api { public static void first() {} }");
        Path second = dependency("second.jar", "package dependency; public class Api { public static void second() {} }");
        Path repository = Files.createDirectory(temporary.resolve("sources"));
        Files.writeString(repository.resolve("Client.java"), "class Client { void run() { dependency.Api.first(); dependency.Api.second(); } }");
        var engine = EngineFactory.create();
        var snapshot = engine.analyze(repository, new AnalysisOptions(List.of(), List.of(first, second)));
        assertEquals(1, snapshot.coverage().resolvedCallCount());
        assertEquals("dependency.Api#first()", snapshot.coverage().externalTargets().getFirst().value());
        var reversed = engine.analyze(repository, new AnalysisOptions(List.of(), List.of(second, first)));
        assertEquals("dependency.Api#second()", reversed.coverage().externalTargets().getFirst().value());
        Files.writeString(repository.resolve("Entity.java"), "@lombok.Data class Entity { private String name; void run() { getName(); } }");
        Path annotation = dependency("annotation.jar", "package lombok; public @interface Data {}");
        var generated = engine.analyze(repository, new AnalysisOptions(List.of(), List.of(annotation)));
        assertTrue(generated.coverage().unresolvedCalls().stream().anyMatch(call -> call.name().equals("getName")));
        assertFalse(generated.methods().keySet().stream().anyMatch(id -> id.contains("#getName(")));
    }
}
