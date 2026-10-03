package com.codeintel.adapter.out.javaparser;

import com.codeintel.adapter.out.filesystem.FileSystemRepositoryScanner;
import com.codeintel.domain.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SymbolResolutionTest {
    private List<SourceUnit> analyze(Path repository, Path... roots) throws Exception {
        return new JavaParserSourceAnalyzer().analyze(new FileSystemRepositoryScanner().scan(repository, List.of(roots)));
    }
    private Path fixture(String name) throws Exception {
        return Path.of(Objects.requireNonNull(getClass().getResource("/fixtures/" + name)).toURI());
    }
    private List<MethodCall> calls(List<SourceUnit> units) {
        return units.stream().flatMap(u -> u.calls().stream()).toList();
    }
    private MethodCall call(List<SourceUnit> units, String expression) {
        return calls(units).stream().filter(c -> c.rawExpression().equals(expression)).findFirst().orElseThrow();
    }
    private Set<MethodId> definitions(List<SourceUnit> units) {
        var ids = new HashSet<MethodId>();
        units.forEach(u -> u.types().forEach(t -> t.methods().forEach(m -> ids.add(m.id()))));
        return ids;
    }
    private void write(Path repository, String path, String source) throws Exception {
        Path file = repository.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    @Test void resolvesCommerceCallsAndReusesEveryCollectedProjectIdentity() throws Exception {
        var units = analyze(fixture("commerce"));
        var ids = definitions(units);
        for (var call : calls(units)) {
            if (call.rawExpression().equals("gateway.deliver(orderId)")) {
                assertEquals(ResolutionStatus.UNRESOLVED, call.status());
                assertFalse(call.failure().orElseThrow().message().isBlank());
                assertEquals("UNSOLVED_SYMBOL", call.failure().orElseThrow().category());
            } else {
                assertEquals(ResolutionStatus.RESOLVED, call.status(), call::toString);
                if (call.target().orElseThrow().declaringType().qualifiedName().startsWith("com.example"))
                    assertTrue(ids.contains(call.target().orElseThrow()), call::toString);
            }
        }
        assertEquals(1, call(units, "payments.pay(orderId)").target().orElseThrow().parameterTypes().size());
        assertEquals(2, call(units, "payments.pay(orderId, 2)").target().orElseThrow().parameterTypes().size());
        assertEquals("<init>", call(units, "this(0);").target().orElseThrow().name());
        assertEquals("com.example.commerce.NestedTypes$Worker", call(units, "new Worker(payments)").target().orElseThrow().declaringType().value());
        assertEquals("validate", call(units, "validate(orderId)").target().orElseThrow().name());
        assertEquals("java.util.Collections", call(units, "emptyList()").target().orElseThrow().declaringType().value());
        assertFalse(ids.contains(call(units, "emptyList()").target().orElseThrow()));
        assertEquals(2, calls(units).stream().filter(c -> c.rawExpression().equals("payments.pay(orderId)") && c.caller().orElseThrow().name().equals("place")).count());
        assertEquals(units, analyze(fixture("commerce")));
    }

    @Test void supportsSubmodulesExplicitRootsAndPackageInferredRoots(@TempDir Path repo) throws Exception {
        write(repo, "module-a/src/main/java/a/A.java", "package a; public class A { public static void run() {} }");
        write(repo, "module-b/src/test/java/b/B.java", "package b; class B { void run() { a.A.run(); new c.C().run(); new d.D().run(); } }");
        write(repo, "generated/c/C.java", "package c; public class C { public void run() {} }");
        write(repo, "loose/d/D.java", "package d; public class D { public void run() {} }");
        var units = analyze(repo, Path.of("generated"));
        assertEquals(5, calls(units).size());
        assertTrue(calls(units).stream().allMatch(c -> c.status() == ResolutionStatus.RESOLVED), () -> calls(units).toString());
        assertTrue(definitions(units).contains(call(units, "a.A.run()").target().orElseThrow()));
    }

    @Test void isolatesFailuresAndNeverUsesEngineRuntimeDependencies(@TempDir Path repo) throws Exception {
        write(repo, "src/main/java/example/Example.java", """
                package example;
                import missing.Dependency;
                import com.github.javaparser.JavaParser;
                class Example {
                    Dependency missing;
                    void run() { "text".length(); missing.send(); new JavaParser().parse("class X {}"); "text".length(); }
                }
                """);
        write(repo, "src/main/java/example/Broken.java", "class Broken {");
        var units = analyze(repo);
        assertEquals(2, calls(units).stream().filter(c -> c.status() == ResolutionStatus.RESOLVED).count());
        assertEquals(3, calls(units).stream().filter(c -> c.status() == ResolutionStatus.UNRESOLVED).count());
        assertTrue(calls(units).stream().filter(c -> c.status() == ResolutionStatus.UNRESOLVED)
                .allMatch(c -> !c.failure().orElseThrow().message().isBlank() && c.failure().orElseThrow().competingTargets().isEmpty()));
        assertTrue(units.stream().anyMatch(u -> u.partial() && u.diagnostics().stream().anyMatch(d -> d.code().equals("PARSE_ERROR"))));
        assertTrue(units.stream().anyMatch(u -> u.diagnostics().stream().anyMatch(d -> d.code().equals("UNSOLVED_SYMBOL"))));
        assertFalse(definitions(units).contains(call(units, "\"text\".length()").target().orElseThrow()));
    }

    @Test void mapsGenericVarargsNestedOverloadsAndInitializerCalls(@TempDir Path repo) throws Exception {
        write(repo, "src/main/java/p/Example.java", """
                package p;
                class Example<T extends Number> {
                    int field = "abc".length();
                    Example() {}
                    void accept(T value) {}
                    void many(String... values) {}
                    void choose(int value) {}
                    void choose(String value) {}
                    static class Nested { Nested(String value) {} void go() {} }
                    void run(T value) {
                        accept(value); many("a", "b"); choose(1); choose("s");
                        new Nested("x").go();
                    }
                }
                """);
        var units = analyze(repo);
        assertTrue(calls(units).stream().allMatch(c -> c.status() == ResolutionStatus.RESOLVED), () -> calls(units).toString());
        var ids = definitions(units);
        for (var c : calls(units)) if (c.target().orElseThrow().declaringType().value().startsWith("p.")) assertTrue(ids.contains(c.target().orElseThrow()), c::toString);
        assertTrue(call(units, "\"abc\".length()").caller().isEmpty());
        assertEquals(1, call(units, "many(\"a\", \"b\")").target().orElseThrow().parameterTypes().getFirst().arrayDimensions());
        assertNotEquals(call(units, "choose(1)").target(), call(units, "choose(\"s\")").target());
    }
    @Test void usesStaticDeclarationsAndErasesExternalGenericVarargs(@TempDir Path repo) throws Exception {
        write(repo, "src/main/java/p/Dispatch.java", """
                package p;
                interface Contract { void run(); }
                class Implementation implements Contract { public void run() {} }
                class Dispatch {
                    void run(Contract contract) {
                        contract.run();
                        java.util.Arrays.asList("a", "b");
                        java.util.Map.Entry.comparingByKey();
                    }
                }
                """);
        var units = analyze(repo);
        assertTrue(calls(units).stream().allMatch(c -> c.status() == ResolutionStatus.RESOLVED), () -> calls(units).toString());
        assertEquals("p.Contract", call(units, "contract.run()").target().orElseThrow().declaringType().value());
        assertEquals("java.lang.Object[]", call(units, "java.util.Arrays.asList(\"a\", \"b\")").target().orElseThrow().parameterTypes().getFirst().identity());
        assertEquals("java.util.Map$Entry", call(units, "java.util.Map.Entry.comparingByKey()").target().orElseThrow().declaringType().value());
    }

    @Test void solverAmbiguityDoesNotAbortOtherOccurrences(@TempDir Path repo) throws Exception {
        write(repo, "src/main/java/p/Ambiguous.java", """
                package p;
                class Ambiguous {
                    void select(String value) {}
                    void select(Integer value) {}
                    void run() { select(null); "ok".length(); }
                }
                """);
        var units = analyze(repo);
        assertEquals(2, calls(units).size());
        assertEquals(ResolutionStatus.RESOLVED, call(units, "\"ok\".length()").status());
        var uncertain = call(units, "select(null)");
        assertEquals(ResolutionStatus.UNRESOLVED, uncertain.status());
        assertFalse(uncertain.failure().orElseThrow().message().isBlank());
        assertTrue(uncertain.failure().orElseThrow().competingTargets().isEmpty());
    }
}
