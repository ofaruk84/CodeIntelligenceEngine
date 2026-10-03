package com.codeintel.adapter.in.cli;

import com.codeintel.application.result.*;
import com.codeintel.application.service.CodeIntelligenceService;
import com.codeintel.domain.model.*;
import java.io.PrintStream;
import java.util.*;

/** Deterministic text presentation; traversal belongs to the application service. */
public final class CliRenderer {
    private final PrintStream out;
    private final PrintStream err;
    public CliRenderer(PrintStream out, PrintStream err) { this.out = out; this.err = err; }
    public static void help(PrintStream out) {
        out.println("""
                Usage: java -jar code-intelligence.jar [options] <command> <repository> [arguments]
                scan <repository>
                search <repository> <query>          (empty quoted query lists all symbols)
                method <repository> <method-id>
                callers <repository> <method-id>
                callees <repository> <method-id>
                dependencies <repository> <method-id>
                path <repository> <source-id> <target-id>
                impact <repository> <method-id>
                Options: --source-root <path> (repeatable, anywhere before --)
                Relative repositories use the working directory; relative roots use the repository.
                Roots augment discovery and must be inside the repository. -- ends option parsing.
                Quote complete IDs, especially parentheses, <init>, and nested-type $ characters.
                Exit: 0 success, 1 fatal failure, 2 syntax, 3 invalid input, 4 unknown ID,
                      5 partial scan/source analysis (results remain available).
                Unresolved calls alone do not change the success exit code.
                """);
    }
    public void summary(AnalysisSnapshot snapshot) {
        long failed = snapshot.sources().stream().filter(unit -> unit.diagnostics().stream()
                .anyMatch(d -> Set.of("PARSE_ERROR", "SOURCE_READ_ERROR", "INVALID_DECLARATION").contains(d.code()))).count();
        long types = snapshot.sources().stream().mapToLong(unit -> unit.types().size()).sum();
        long calls = snapshot.sources().stream().mapToLong(unit -> unit.calls().size()).sum();
        var c = snapshot.coverage();
        out.printf(Locale.ROOT, "Scan: scanned=%d parsed=%d failed=%d types=%d callables=%d call-sites=%d%n",
                snapshot.repository().files().size(), snapshot.sources().size() - failed, failed, types, snapshot.methods().size(), calls);
        out.printf(Locale.ROOT, "Resolution: resolved=%d unresolved=%d ambiguous=%d external-targets=%d without-caller=%d%n",
                c.resolvedCallCount(), c.unresolvedCalls().size(), c.ambiguousCalls().size(), c.externalTargets().size(), c.callsWithoutCaller().size());
        out.println("Source roots: " + snapshot.repository().sourceRoots().stream().map(p -> p.toString().replace('\\', '/')).sorted().toList());
    }
    public void coverage(ResolutionCoverage c) {
        if (c.incomplete()) err.println("Coverage incomplete: resolved reachability is a lower bound; static calls do not prove runtime execution.");
        c.scanDiagnostics().forEach(d -> err.println("Scan diagnostic " + d.code() + " " + d.path().toString().replace('\\', '/') + ": " + line(d.message())));
        c.sourceDiagnostics().forEach(d -> {
            err.println("Source diagnostic " + d.severity() + " " + d.code() + " " + d.location().map(CliRenderer::location).orElse("(no range)") + ": " + line(d.message()));
            d.relatedLocations().forEach(l -> err.println("  Related: " + location(l)));
        });
        c.partialSources().forEach(path -> err.println("Partial source: " + path));
        var calls = new ArrayList<MethodCall>(c.unresolvedCalls()); calls.addAll(c.ambiguousCalls());
        calls.sort(Comparator.comparing((MethodCall call) -> call.location().path()).thenComparingInt(call -> call.location().startLine()).thenComparingInt(call -> call.location().startColumn()));
        calls.forEach(call -> err.println("Snapshot-wide " + call.status() + " " + location(call.location())
                + " caller=" + call.caller().map(MethodId::value).orElse("(none)") + " expression=" + line(call.rawExpression())
                + " failure=" + line(call.failure().orElseThrow().toString())));
    }
    public boolean query(CliArguments args, CodeIntelligenceService service, AnalysisSnapshot snapshot) {
        var operands = args.operands();
        switch (args.command()) {
            case "scan" -> { }
            case "search" -> {
                var symbols = service.searchSymbol(operands.getFirst()).symbols();
                if (symbols.isEmpty()) out.println("No matching symbols.");
                symbols.forEach(s -> out.println(s.kind() + " " + s.id() + " " + location(s.location())));
            }
            case "method" -> {
                var method = service.getMethod(operands.getFirst());
                if (method.isPresent()) {
                    var m = method.orElseThrow();
                    out.println(m.kind() + " " + m.id().value() + " " + location(m.location()));
                    out.println("Return: " + m.returnType().map(TypeReference::name).orElse("(constructor)"));
                    out.println("Modifiers: " + m.modifiers().stream().sorted().toList());
                } else if (snapshot.graphIds().containsKey(operands.getFirst())) {
                    out.println("Known external target; no analyzed source definition: " + operands.getFirst());
                } else { err.println("Unknown graph ID: " + operands.getFirst()); return false; }
            }
            case "callers" -> ids("Direct callers", service.findCallers(operands.getFirst()));
            case "callees" -> ids("Direct callees", service.findCallees(operands.getFirst()));
            case "dependencies" -> distances("Dependencies", service.findDependencies(operands.getFirst()).minimumDistances());
            case "path" -> {
                var result = service.findPath(operands.getFirst(), operands.get(1));
                out.println(result.path().map(p -> "Path: " + String.join(" -> ", p.stream().map(MethodId::value).toList()))
                        .orElse("No path between known graph IDs."));
            }
            case "impact" -> {
                var impact = service.analyzeChangeImpact(operands.getFirst());
                out.println("Observed impact of " + impact.target().value());
                ids("Direct callers", impact.directCallers()); ids("Indirect callers", impact.indirectCallers());
                distances("Affected methods (minimum depth)", impact.minimumDepths());
                out.println("Affected classes: " + impact.affectedClasses().stream().map(SymbolId::value).toList());
                out.println("Maximum minimum depth: " + impact.maximumDepth());
                out.println("Unresolved/ambiguous calls originating in affected methods: " + impact.unresolvedCallsInAffectedMethods().size());
                impact.unresolvedCallsInAffectedMethods().forEach(call -> out.println("  " + location(call.location())
                        + " caller=" + call.caller().orElseThrow().value() + " expression=" + line(call.rawExpression())));
                out.println("These unresolved calls are not proven to reach the changed target. Snapshot-wide coverage is reported on stderr.");
            }
            default -> throw new IllegalStateException("Unsupported parsed command");
        }
        return true;
    }
    private void ids(String label, List<MethodId> ids) {
        out.println(label + ":" + (ids.isEmpty() ? " (empty)" : ""));
        ids.forEach(id -> out.println("  " + id.value()));
    }
    private void distances(String label, Map<MethodId, Integer> distances) {
        out.println(label + ":" + (distances.isEmpty() ? " (empty)" : ""));
        distances.forEach((id, distance) -> out.println("  " + id.value() + " depth=" + distance));
    }
    private static String location(SourceLocation l) { return l.path() + ":" + l.startLine() + ":" + l.startColumn(); }
    private static String line(String text) { return text.replace('\r', ' ').replace('\n', ' '); }
}
