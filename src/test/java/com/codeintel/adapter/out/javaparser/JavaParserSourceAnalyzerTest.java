package com.codeintel.adapter.out.javaparser;

import com.codeintel.application.result.RepositorySources;
import com.codeintel.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaParserSourceAnalyzerTest {
    private final JavaParserSourceAnalyzer analyzer = new JavaParserSourceAnalyzer();

    @Test void extractsDefinitionsAndFallbackIdentities() {
        var unit = analyzer.parse("src/Outer.java", """
                package sample;
                import java.util.List;
                import static java.util.Collections.*;
                public class Outer extends Base implements Runnable {
                    private int first, second[];
                    public Outer(String value) throws Failure { super(); }
                    void work(int value) {}
                    void work(@Deprecated List<String>... values) {}
                    class Inner { Inner(int value) {} }
                }
                """);
        assertFalse(unit.partial());
        assertEquals("sample", unit.packageName());
        assertEquals(2, unit.imports().size());
        assertTrue(unit.imports().get(1).isStatic());
        assertTrue(unit.imports().get(1).onDemand());
        var outer = unit.types().getFirst();
        assertEquals("sample.Outer", outer.id().value());
        assertEquals(Set.of("public"), outer.modifiers());
        assertEquals(List.of("Base", "Runnable"), outer.supertypes().stream().map(TypeReference::name).toList());
        assertEquals(List.of("first", "second"), outer.fields().stream().map(FieldInfo::name).toList());
        assertEquals(1, outer.fields().get(1).type().arrayDimensions());
        assertEquals(3, outer.methods().size());
        var constructor = outer.methods().getFirst();
        assertEquals(CallableKind.CONSTRUCTOR, constructor.kind());
        assertTrue(constructor.returnType().isEmpty());
        assertEquals("Failure", constructor.thrownTypes().getFirst().name());
        assertTrue(constructor.id().declaration().isPresent());
        assertEquals("sample.Outer#work(int)", outer.methods().get(1).id().value());
        var varargs = outer.methods().get(2).parameters().getFirst();
        assertTrue(varargs.varargs());
        assertEquals(new TypeReference("List<String>", 1, false), varargs.type());
        assertEquals("sample.Outer$Inner#<init>(int)", unit.types().get(1).methods().getFirst().id().value());
        assertEquals(unit, analyzer.parse("src/Outer.java", """
                package sample;
                import java.util.List;
                import static java.util.Collections.*;
                public class Outer extends Base implements Runnable {
                    private int first, second[];
                    public Outer(String value) throws Failure { super(); }
                    void work(int value) {}
                    void work(@Deprecated List<String>... values) {}
                    class Inner { Inner(int value) {} }
                }
                """));
    }

    @Test void preservesCallsAndLexicalOwnershipAcrossLambdasAndTypes() {
        var unit = analyzer.parse("Example.java", """
                class Example {
                    Object field = factory();
                    { initialize(); }
                    Example() { this(1); }
                    Example(int n) { super(); }
                    void run() {
                        target /* keep */ . ping(); target.ping();
                        Runnable task = () -> nested();
                        class Local { Object field = localField(); void go() { local(); } }
                        Object value = new Object() { Object field = anonymousField(); void go() { anonymous(); } };
                    }
                }
                """);
        assertFalse(unit.partial());
        assertEquals(12, unit.calls().size());
        assertEquals("", unit.packageName());
        assertEquals(2, unit.calls().stream().filter(c -> c.name().equals("ping")).count());
        var ping = unit.calls().stream().filter(c -> c.name().equals("ping")).findFirst().orElseThrow();
        assertEquals("target /* keep */ . ping()", ping.rawExpression());
        assertEquals("target", ping.scope().orElseThrow());
        assertEquals(7, ping.location().startLine());
        assertEquals(9, ping.location().startColumn());
        assertEquals("run", ping.caller().orElseThrow().name());
        for (String name : List.of("factory", "initialize", "localField", "anonymousField"))
            assertTrue(unit.calls().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().caller().isEmpty());
        assertEquals("run", unit.calls().stream().filter(c -> c.name().equals("nested")).findFirst().orElseThrow().caller().orElseThrow().name());
        for (String name : List.of("local", "anonymous")) {
            var caller = unit.calls().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().caller().orElseThrow();
            assertEquals("go", caller.name());
            assertTrue(caller.declaringType().declaration().isPresent());
        }
        assertTrue(unit.calls().stream().allMatch(c -> c.status() == ResolutionStatus.UNRESOLVED
                && c.failure().orElseThrow().category().equals("NOT_ATTEMPTED")));
    }

    @Test void supportsModernTypesAndRecordPatterns() {
        var unit = analyzer.parse("Kinds.java", """
                @interface Label { String value() default "test"; }
                interface Action { void run(); }
                enum Color { RED; void show() { paint(); } }
                record Message(String text) {
                    Message { validate(text); }
                    int length(Object value) {
                        return switch (value) {
                            case Message(String text) -> text.length();
                            default -> 0;
                        };
                    }
                }
                """);
        assertFalse(unit.partial(), () -> unit.diagnostics().toString());
        assertEquals(List.of(TypeKind.ANNOTATION, TypeKind.INTERFACE, TypeKind.ENUM, TypeKind.RECORD),
                unit.types().stream().map(ClassNode::kind).toList());
        var compact = unit.types().get(3).methods().getFirst();
        assertEquals(CallableKind.CONSTRUCTOR, compact.kind());
        assertEquals("text", compact.parameters().getFirst().name());
        assertEquals(compact.id(), unit.calls().stream().filter(c -> c.name().equals("validate")).findFirst().orElseThrow().caller().orElseThrow());
    }

    @Test void continuesAfterMalformedAndUnreadableSources(@TempDir Path repository) throws Exception {
        Files.writeString(repository.resolve("Bad.java"), "class Bad { void broken( { }");
        Files.writeString(repository.resolve("Good.java"), "class Good { void run() { call(); } }");
        var sources = new RepositorySources(repository, List.of(Path.of("Bad.java"), Path.of("Missing.java"),
                Path.of("Good.java")), List.of(), List.of(), false);
        var units = analyzer.analyze(sources);
        assertEquals(List.of("Bad.java", "Missing.java", "Good.java"), units.stream().map(SourceUnit::path).toList());
        assertTrue(units.getFirst().partial());
        assertTrue(units.getFirst().types().isEmpty());
        assertEquals("PARSE_ERROR", units.getFirst().diagnostics().getFirst().code());
        assertEquals(DiagnosticSeverity.ERROR, units.getFirst().diagnostics().getFirst().severity());
        assertFalse(units.getFirst().diagnostics().getFirst().message().isBlank());
        assertEquals("SOURCE_READ_ERROR", units.get(1).diagnostics().getFirst().code());
        assertFalse(units.get(2).partial());
        assertEquals("Good", units.get(2).types().getFirst().id().value());
        assertEquals(1, units.get(2).calls().size());
        assertEquals(units, analyzer.analyze(sources));
    }
    @Test void keepsAnonymousConstructorArgumentsInTheOuterCallable() {
        var unit = analyzer.parse("Example.java", """
                class Example {
                    void run() {
                        Object value = new Base(argument()) { Object field = body(); };
                    }
                }
                """);
        assertFalse(unit.partial());
        assertEquals("run", unit.calls().stream().filter(c -> c.name().equals("argument"))
                .findFirst().orElseThrow().caller().orElseThrow().name());
        assertTrue(unit.calls().stream().filter(c -> c.name().equals("body"))
                .findFirst().orElseThrow().caller().isEmpty());
    }

    @Test void supportsEnumConstantBodiesAndQualifiedMembersOfLocalTypes() {
        var unit = analyzer.parse("Example.java", """
                enum Example {
                    FIRST { void run() { call(); } };
                    void method() { class Local { class Inner { void run() { nested(); } } } }
                }
                """);
        assertFalse(unit.partial());
        assertEquals(4, unit.types().size());
        assertEquals(ClassNode.Nesting.ANONYMOUS, unit.types().get(1).nesting());
        var member = unit.types().get(3);
        assertEquals(ClassNode.Nesting.MEMBER, member.nesting());
        assertEquals("Example$Local$Inner", member.id().qualifiedName());
        assertTrue(member.id().declaration().isPresent());
        assertEquals(member.methods().getFirst().id(), unit.calls().get(1).caller().orElseThrow());
    }

    @Test void retainsRawTextWithTabsAndDifferentLineEndings() {
        for (String newline : List.of("\n", "\r\n", "\r")) {
            String source = "class Example {" + newline + "\tvoid run() { service . call(); }" + newline + "}";
            var unit = analyzer.parse("Example.java", source);
            assertFalse(unit.partial());
            var call = unit.calls().getFirst();
            assertEquals("service . call()", call.rawExpression());
            assertEquals(2, call.location().startLine());
            assertEquals(15, call.location().startColumn());
        }
    }

}

