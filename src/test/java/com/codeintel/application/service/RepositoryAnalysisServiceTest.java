package com.codeintel.application.service;

import com.codeintel.adapter.out.filesystem.FileSystemRepositoryScanner;
import com.codeintel.adapter.out.javaparser.JavaParserSourceAnalyzer;
import com.codeintel.adapter.out.graph.InMemoryCodeGraph;
import com.codeintel.application.result.*;
import com.codeintel.domain.model.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryAnalysisServiceTest {
    @TempDir Path root;
    @Test void parsedFixtureAnswersComeFromOneScanAnalysisAndGraphBuild() throws Exception {
        var scans = new AtomicInteger(); var analyses = new AtomicInteger(); var builds = new AtomicInteger();
        var fixture = Path.of(Objects.requireNonNull(getClass().getResource("/fixtures/commerce")).toURI());
        var orchestration = new RepositoryAnalysisService((path, roots) -> {
            scans.incrementAndGet(); return new FileSystemRepositoryScanner().scan(path, roots);
        }, sources -> {
            analyses.incrementAndGet(); return new JavaParserSourceAnalyzer().analyze(sources);
        }, units -> { builds.incrementAndGet(); return InMemoryCodeGraph.fromSources(units); });
        var snapshot = orchestration.analyze(fixture);
        var service = new CodeIntelligenceService(snapshot);
        var pay = snapshot.methods().values().stream().map(MethodNode::id)
                .filter(id -> id.declaringType().qualifiedName().endsWith("PaymentService")
                        && id.name().equals("pay") && id.parameterTypes().size() == 1).findFirst().orElseThrow();
        var retry = snapshot.methods().values().stream().map(MethodNode::id)
                .filter(id -> id.name().equals("retry")).findFirst().orElseThrow();
        var overloadedPay = snapshot.methods().values().stream().map(MethodNode::id)
                .filter(id -> id.name().equals("pay") && id.parameterTypes().size() == 2).findFirst().orElseThrow();
        assertTrue(service.findCallers(overloadedPay.value()).contains(retry));
        assertTrue(service.analyzeChangeImpact(overloadedPay.value()).directCallers().contains(retry));
        assertTrue(service.analyzeChangeImpact(pay.value()).indirectCallers().contains(retry));
        assertEquals(List.of(retry, overloadedPay, pay), service.findPath(retry.value(), pay.value()).path().orElseThrow());
        assertTrue(service.findDependencies(retry.value()).minimumDistances().containsKey(pay));
        assertTrue(service.getMethod(pay.value()).isPresent());
        assertEquals(2, service.searchSymbol("#pay(").symbols().size());
        assertEquals(List.of(overloadedPay), service.findCallees(retry.value()));
        assertFalse(snapshot.coverage().unresolvedCalls().isEmpty());
        for (int i = 0; i < 3; i++) service.analyzeChangeImpact(pay.value());
        assertEquals(1, scans.get()); assertEquals(1, analyses.get()); assertEquals(1, builds.get());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.methods().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.sources().clear());
    }
    @Test void coverageRetainsFailuresAndSeparatesAffectedOriginEvidence() throws Exception {
        Files.writeString(root.resolve("Example.java"), """
                package example;
                class Example {
                    void target() {}
                    void caller() { target(); missing(); }
                    void unrelated() { otherMissing(); }
                }
                """);
        Files.writeString(root.resolve("Broken.java"), "class Broken { void broken( }");
        var scanner = new FileSystemRepositoryScanner();
        var service = new RepositoryAnalysisService((path, roots) -> {
            var scanned = scanner.scan(path, roots);
            return new RepositorySources(scanned.repository(), scanned.files(), scanned.sourceRoots(),
                    List.of(new RepositorySources.Diagnostic("SCAN_ACCESS_ERROR", Path.of("hidden"), "Access denied")), true);
        }, new JavaParserSourceAnalyzer(), InMemoryCodeGraph::fromSources);
        var snapshot = service.analyze(root);
        var target = snapshot.methods().values().stream().map(MethodNode::id)
                .filter(id -> id.name().equals("target")).findFirst().orElseThrow();
        var impact = new CodeIntelligenceService(snapshot).analyzeChangeImpact(target.value());
        assertTrue(impact.coverage().partialScan());
        assertEquals(List.of("Broken.java"), impact.coverage().partialSources());
        assertTrue(impact.coverage().sourceDiagnostics().stream().anyMatch(d -> d.code().equals("PARSE_ERROR")));
        assertEquals(2, impact.coverage().unresolvedCalls().size());
        assertEquals(1, impact.unresolvedCallsInAffectedMethods().size());
        assertEquals("missing", impact.unresolvedCallsInAffectedMethods().getFirst().name());
        assertEquals(1, impact.maximumDepth());
        assertThrows(UnsupportedOperationException.class, () -> impact.coverage().unresolvedCalls().clear());
        assertThrows(UnsupportedOperationException.class, () -> impact.unresolvedCallsInAffectedMethods().clear());
    }
    @Test void optionsAreCopiedAndInvalidRepositoriesRetainScannerFailure() {
        var roots = new ArrayList<Path>(); roots.add(Path.of("extra"));
        var options = new AnalysisOptions(roots); roots.clear();
        assertEquals(List.of(Path.of("extra")), options.additionalRoots());
        var service = new RepositoryAnalysisService(new FileSystemRepositoryScanner(),
                new JavaParserSourceAnalyzer(), InMemoryCodeGraph::fromSources);
        assertThrows(java.io.IOException.class, () -> service.analyze(root.resolve("missing")));
    }
}
