package com.codeintel.adapter.out.javaparser;

import com.codeintel.bootstrap.EngineFactory;
import com.codeintel.application.service.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalIdentityTest {
    @TempDir Path repository;

    @Test void finalizesSignaturesAndEveryGraphReference() throws Exception {
        Path root = Files.createDirectories(repository.resolve("src/main/java/p"));
        Path imported = Files.createDirectories(repository.resolve("src/main/java/q"));
        Files.writeString(imported.resolve("Imported.java"), "package q; public class Imported {}");
        Files.writeString(root.resolve("Example.java"), """
                package p;
                import java.util.List;
                import q.Imported;
                class User {}
                class Example<T extends Number> {
                    Example(String s) {} Example(User u) {}
                    static class Nested {}
                    void accept(String s, User u, Nested n, String[][] a, List<String> l, T t, T[] numbers, Imported imported, String... rest) {}
                    <V> void generic(V value) {}
                    <V extends CharSequence & Comparable<V>> void bounded(V value) {}
                    void missing(Absent value) { "ok".length(); }
                    void run(User u, T t) { accept("s", u, null, null, null, t, null, null, "r"); generic("s"); bounded("s"); new Example("s"); new Example(u); }
                }
                """);
        var snapshot = EngineFactory.create().analyze(repository);
        var ids = snapshot.methods().keySet();
        assertTrue(ids.contains("p.Example#accept(java.lang.String,p.User,p.Example.Nested,java.lang.String[][],java.util.List,java.lang.Number,java.lang.Number[],q.Imported,java.lang.String[])"), ids::toString);
        assertTrue(ids.contains("p.Example#generic(java.lang.Object)"), ids::toString);
        assertTrue(ids.contains("p.Example#bounded(java.lang.CharSequence)"), ids::toString);
        assertTrue(ids.contains("p.Example#<init>(java.lang.String)"));
        assertTrue(ids.contains("p.Example#<init>(p.User)"));
        var fallback = ids.stream().filter(id -> id.contains("#missing(")).findFirst().orElseThrow();
        assertTrue(fallback.contains("unresolved:6:Absent"));
        assertTrue(fallback.contains("src/main/java/p/Example.java:"));
        for (var c : snapshot.sources().getFirst().calls()) {
            assertTrue(ids.contains(c.caller().orElseThrow().value()));
            c.target().filter(id -> id.declaringType().value().startsWith("p.")).ifPresent(id -> assertTrue(ids.contains(id.value())));
        }
        assertEquals(ids, snapshot.graphIds().keySet().stream().filter(id -> id.startsWith("p.")).collect(java.util.stream.Collectors.toSet()));
        var service = new CodeIntelligenceService(snapshot);
        assertTrue(service.getMethod("p.Example#generic(java.lang.Object)").isPresent());
        assertEquals(1, service.analyzeChangeImpact("p.Example#generic(java.lang.Object)").directCallers().size());
        assertEquals(snapshot.sources(), EngineFactory.create().analyze(repository).sources());
    }

    @Test void qualifiesErasureCollisionsAndKeepsCallerOwnership() throws Exception {
        Files.writeString(repository.resolve("Duplicate.java"), """
                import java.util.List;
                class Duplicate {
                    void same(List<String> values) { "a".length(); }
                    void same(List<Integer> values) { "b".length(); }
                }
                """);
        var snapshot = EngineFactory.create().analyze(repository);
        assertEquals(2, snapshot.methods().size());
        assertTrue(snapshot.methods().values().stream().allMatch(m -> m.id().declaration().isPresent()));
        assertEquals(2, snapshot.sources().getFirst().diagnostics().stream().filter(d -> d.code().equals("IDENTITY_COLLISION")).count());
        var calls = snapshot.sources().getFirst().calls();
        assertNotEquals(calls.get(0).caller(), calls.get(1).caller());
        assertTrue(calls.stream().allMatch(c -> snapshot.methods().containsKey(c.caller().orElseThrow().value())));
    }
}
