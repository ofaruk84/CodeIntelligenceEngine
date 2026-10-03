package com.codeintel.adapter.out.javaparser;

import com.codeintel.adapter.out.filesystem.FileSystemRepositoryScanner;
import com.codeintel.domain.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises resource sources through the public scanner/analyzer contracts. */
class SyntheticFixtureTest {
    private final JavaParserSourceAnalyzer analyzer = new JavaParserSourceAnalyzer();
    private Path fixture(String name) throws Exception {
        return Path.of(Objects.requireNonNull(getClass().getResource("/fixtures/" + name)).toURI());
    }
    private List<SourceUnit> commerce() throws Exception {
        return analyzer.analyze(new FileSystemRepositoryScanner().scan(fixture("commerce")));
    }
    private SourceUnit unit(List<SourceUnit> units, String type) {
        return units.stream().filter(u -> u.path().endsWith("/" + type + ".java")).findFirst().orElseThrow();
    }
    @Test void extractsDefinitionsOverloadsAndNestedIdentities() throws Exception {
        var units = commerce();
        assertEquals(13, units.size());
        assertTrue(units.stream().noneMatch(SourceUnit::partial));
        var orders = unit(units, "OrderService");
        assertEquals("com.example.commerce", orders.packageName());
        assertEquals(List.of("java.util.List", "java.util.Collections.emptyList"), orders.imports().stream().map(ImportInfo::qualifiedName).toList());
        assertTrue(orders.imports().get(1).isStatic());
        var type = orders.types().getFirst();
        assertEquals(TypeKind.CLASS, type.kind());
        assertEquals(List.of("payments", "inventory", "notifications"), type.fields().stream().map(FieldInfo::name).toList());
        assertTrue(type.fields().stream().allMatch(f -> f.modifiers().equals(Set.of("private", "final"))));
        var constructor = type.methods().stream().filter(m -> m.kind() == CallableKind.CONSTRUCTOR).findFirst().orElseThrow();
        assertEquals(List.of("com.example.commerce.PaymentService", "com.example.commerce.InventoryService", "com.example.commerce.NotificationService"), constructor.parameters().stream().map(p -> p.type().name()).toList());
        assertEquals(List.of("payments", "inventory", "notifications"), constructor.parameters().stream().map(ParameterInfo::name).toList());
        assertTrue(constructor.returnType().isEmpty());
        var overloads = unit(units, "PaymentService").types().getFirst().methods().stream().filter(m -> m.id().name().equals("pay")).toList();
        assertEquals(List.of(1, 2), overloads.stream().map(m -> m.parameters().size()).toList());
        assertEquals(2, overloads.stream().map(MethodNode::id).distinct().count());
        assertTrue(overloads.stream().allMatch(m -> m.id().declaration().isEmpty()));
        var worker = unit(units, "NestedTypes").types().get(1);
        assertEquals("com.example.commerce.NestedTypes$Worker", worker.id().value());
        assertEquals(ClassNode.Nesting.MEMBER, worker.nesting());
        assertEquals(3, worker.methods().stream().map(MethodNode::id).distinct().count());
        assertEquals(units, commerce());
    }
    @Test void preservesCallsOwnershipLocationsAndUnresolvedEvidence() throws Exception {
        var units = commerce();
        var orders = unit(units, "OrderService");
        var place = orders.types().getFirst().methods().stream().filter(m -> m.id().name().equals("place")).findFirst().orElseThrow();
        var calls = orders.calls().stream().filter(c -> c.caller().filter(place.id()::equals).isPresent()).toList();
        assertEquals(List.of("validate", "pay", "pay", "reserve", "confirm"), calls.stream().map(MethodCall::name).toList());
        assertEquals("payments.pay(orderId)", calls.get(1).rawExpression());
        assertEquals(calls.get(1).rawExpression(), calls.get(2).rawExpression());
        assertNotEquals(calls.get(1).location(), calls.get(2).location());
        var retry = unit(units, "RetryPaymentJob").calls().getFirst();
        assertEquals("payments.pay(orderId, 2)", retry.rawExpression());
        assertEquals("retry", retry.caller().orElseThrow().name());
        assertEquals("payments", retry.scope().orElseThrow());
        for (var u : units) {
            var lines = Files.readAllLines(fixture("commerce").resolve(u.path()));
            for (var call : u.calls()) {
                var location = call.location();
                assertEquals(u.path(), location.path());
                assertEquals(location.startLine(), location.endLine());
                assertEquals(call.rawExpression(), lines.get(location.startLine() - 1).substring(location.startColumn() - 1, location.endColumn()));
                if (call.status() == ResolutionStatus.UNRESOLVED) {
                    assertTrue(call.target().isEmpty());
                    assertFalse(call.failure().orElseThrow().message().isBlank());
                    assertNotEquals("NOT_ATTEMPTED", call.failure().orElseThrow().category());
                } else {
                    assertTrue(call.target().isPresent());
                    assertTrue(call.failure().isEmpty());
                }
                assertTrue(u.types().stream().flatMap(t -> t.methods().stream()).anyMatch(m -> call.caller().filter(m.id()::equals).isPresent()));
            }
        }
        assertEquals("gateway.deliver(orderId)", unit(units, "UnresolvedClient").calls().getFirst().rawExpression());
        for (String cycle : List.of("CycleA", "CycleB", "CycleC"))
            assertEquals("next.run()", unit(units, cycle).calls().getFirst().rawExpression());
        assertTrue(unit(units, "PaymentService").calls().stream().anyMatch(c -> c.rawExpression().equals("this(0);")));
        assertTrue(unit(units, "NestedTypes").calls().stream().anyMatch(c -> c.rawExpression().equals("new Worker(payments)")));
    }
    @Test void extractsDocumentedCollaboratorCallSitesWithoutAssumingResolvedEdges() throws Exception {
        var units = commerce();
        var expected = Map.of(
                "OrderController", List.of("orders.place(orderId)"),
                "InventoryService", List.of("repository.save(orderId)"),
                "NotificationService", List.of("format(orderId)"),
                "RetryPaymentJob", List.of("payments.pay(orderId, 2)"));
        for (var entry : expected.entrySet())
            assertEquals(entry.getValue(), unit(units, entry.getKey()).calls().stream().map(MethodCall::rawExpression).toList());
        assertEquals(List.of("com.example.commerce.CycleB", "com.example.commerce.CycleC", "com.example.commerce.CycleA"), List.of("CycleA", "CycleB", "CycleC").stream()
                .map(name -> unit(units, name).types().getFirst().fields().getFirst().type().name()).toList());
        assertEquals(List.of("com.example.commerce.InventoryRepository"), unit(units, "InventoryService").types().getFirst().fields().stream().map(f -> f.type().name()).toList());
    }
    @Test void isolatesMalformedResourcesAndRetainsValidBatchResults() throws Exception {
        var sources = new FileSystemRepositoryScanner().scan(fixture(""));
        var units = analyzer.analyze(sources);
        assertEquals(sources.files().size(), units.size());
        var broken = units.stream().filter(u -> u.path().equals("malformed/Broken.java")).findFirst().orElseThrow();
        assertTrue(broken.partial());
        assertTrue(broken.types().isEmpty());
        assertTrue(broken.calls().isEmpty());
        assertFalse(broken.diagnostics().isEmpty());
        assertTrue(broken.diagnostics().stream().allMatch(d -> d.code().equals("PARSE_ERROR") && d.severity() == DiagnosticSeverity.ERROR && !d.message().isBlank()));
        assertTrue(broken.diagnostics().stream().flatMap(d -> d.location().stream()).allMatch(l -> l.path().equals(broken.path())));
        assertEquals(15, units.stream().filter(u -> !u.partial()).count());
        assertEquals(commerce().stream().map(SourceUnit::packageName).toList(), units.stream().filter(u -> u.path().startsWith("commerce/")).map(SourceUnit::packageName).toList());
        for (var standalone : commerce()) {
            var batch = units.stream().filter(u -> u.path().equals("commerce/" + standalone.path())).findFirst().orElseThrow();
            assertEquals(standalone.types().size(), batch.types().size());
            assertEquals(standalone.calls().stream().map(MethodCall::rawExpression).toList(), batch.calls().stream().map(MethodCall::rawExpression).toList());
        }
        assertEquals(units, analyzer.analyze(sources));
    }
}
