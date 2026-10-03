package com.codeintel.application.result;

import com.codeintel.domain.graph.CodeGraph;
import com.codeintel.domain.model.*;
import java.util.*;

/** Snapshot-wide evidence, never proof that an unresolved call reaches a queried target. */
public record ResolutionCoverage(boolean partialScan, List<RepositorySources.Diagnostic> scanDiagnostics,
        List<String> partialSources, List<AnalysisDiagnostic> sourceDiagnostics,
        List<MethodCall> unresolvedCalls, List<MethodCall> ambiguousCalls,
        List<MethodCall> callsWithoutCaller, List<MethodId> externalTargets, long resolvedCallCount) {
    public ResolutionCoverage {
        scanDiagnostics = List.copyOf(scanDiagnostics);
        partialSources = List.copyOf(partialSources);
        sourceDiagnostics = List.copyOf(sourceDiagnostics);
        unresolvedCalls = List.copyOf(unresolvedCalls);
        ambiguousCalls = List.copyOf(ambiguousCalls);
        callsWithoutCaller = List.copyOf(callsWithoutCaller);
        externalTargets = List.copyOf(externalTargets);
    }
    public boolean incomplete() {
        return partialScan || !partialSources.isEmpty() || !unresolvedCalls.isEmpty()
                || !ambiguousCalls.isEmpty() || !callsWithoutCaller.isEmpty() || !externalTargets.isEmpty();
    }
    static ResolutionCoverage from(RepositorySources repository, List<SourceUnit> units,
                                   CodeGraph graph, Set<String> definitions) {
        var ordered = units.stream().sorted(Comparator.comparing(SourceUnit::path)).toList();
        var calls = ordered.stream().flatMap(unit -> unit.calls().stream()).toList();
        return new ResolutionCoverage(repository.partial(), repository.diagnostics(),
                ordered.stream().filter(SourceUnit::partial).map(SourceUnit::path).toList(),
                ordered.stream().flatMap(unit -> unit.diagnostics().stream()).toList(),
                calls.stream().filter(call -> call.status() == ResolutionStatus.UNRESOLVED).toList(),
                calls.stream().filter(call -> call.status() == ResolutionStatus.AMBIGUOUS).toList(),
                calls.stream().filter(call -> call.caller().isEmpty()).toList(),
                graph.nodes().stream().filter(id -> !definitions.contains(id.value()))
                        .sorted(Comparator.comparing(MethodId::value)).toList(),
                calls.stream().filter(call -> call.status() == ResolutionStatus.RESOLVED).count());
    }
}
