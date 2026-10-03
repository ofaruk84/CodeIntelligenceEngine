# Application API

The application uses scanner, source-analyzer and graph-factory ports to keep concrete adapters outside orchestration. See [CLI.md](CLI.md) for executable usage and [VERIFICATION.md](VERIFICATION.md) for current acceptance evidence.

## Composition and use

The composition root wires adapters outside the application layer. This complete example uses verified fixture IDs and reuses one snapshot for every query:

```java
import com.codeintel.bootstrap.EngineFactory;
import com.codeintel.application.service.AnalysisOptions;
import com.codeintel.application.service.CodeIntelligenceService;
import java.nio.file.Path;

public class ApiExample {
    public static void main(String[] args) throws Exception {
        var snapshot = EngineFactory.create().analyze(
                Path.of("src/test/resources/fixtures/commerce"), AnalysisOptions.defaults());
        var queries = new CodeIntelligenceService(snapshot);
        String target = "com.example.commerce.CycleA#run()";
        var symbols = queries.searchSymbol("Cycle");
        var definition = queries.getMethod(target); // Optional<MethodNode>
        var callers = queries.findCallers(target); // List<MethodId>: CycleC#run()
        var callees = queries.findCallees(target); // List<MethodId>: CycleB#run()
        var dependencies = queries.findDependencies(target); // B: 1, C: 2
        var path = queries.findPath(target, "com.example.commerce.CycleC#run()");
        var impact = queries.analyzeChangeImpact(target); // C: 1, B: 2; excludes A
        var coverage = impact.coverage(); // snapshot-wide evidence
        var scopedFailures = impact.unresolvedCallsInAffectedMethods(); // empty here
        System.out.println(path.path()); // Optional containing A -> B -> C
    }
}
```

Run from the project directory with the packaged JAR on the classpath: `javac -cp target/code-intelligence.jar ApiExample.java`, then `java -cp 'target/code-intelligence.jar;.' ApiExample` in PowerShell. POSIX uses `java -cp 'target/code-intelligence.jar:.' ApiExample`.

Repository is required. Optional roots and local dependency metadata use `new AnalysisOptions(List.of(Path.of("custom/java")), List.of(Path.of("/local/dependency.jar")))` with `java.util.List` imported. Both lists may be empty. Relative roots use the repository; relative JARs use the working directory. Invalid repositories propagate `IOException`; individual scan/analysis failures remain in the snapshot. Graph factories must return immutable indexed graphs. One analysis request scans, analyzes and constructs the graph once; subsequent queries reuse its immutable indexes.

For arbitrary repositories, discover exact IDs with `searchSymbol` and inspect the returned kind rather than assuming a signature. The commerce String payment overload is `com.example.commerce.PaymentService#pay(unresolved:6:String)@54:src/main/java/com/example/commerce/PaymentService.java:6:5-6:55`. Reference parameters retain first-pass AST spellings and declaration qualifiers even when second-pass calls resolve successfully. This identity fallback is separate from a call's `UNRESOLVED` status. See [identity rules](DOMAIN_MODEL.md) and [target mapping](SYMBOL_RESOLUTION.md).

## Query semantics

- Search includes source type and callable definitions, including constructors and overloads, ordered by exact ID using Java string ordering. Matching uses case-insensitive substring checks on names and IDs with `Locale.ROOT`; empty text returns every supported source symbol. External graph nodes are not invented source symbols.
- `getMethod` performs exact string ID lookup and returns `Optional<MethodNode>`. Unknown IDs and external nodes without source definitions both return absence. Canonical, nested, constructor and fallback IDs pass through unchanged.
- Graph queries reject unknown IDs with `UnknownMethodException`, which carries an immutable ID list. Path queries report all unknown endpoints, deduplicated and sorted. Known isolated and external nodes are valid query targets.
- Direct neighbors are unique and ordered by ID, retaining explicit self-edges.
- Dependencies return transitive forward reachability and minimum BFS distances, excluding the starting node even through cycles. Distance maps iterate in ID order.
- Paths reuse deterministic BFS: the first shortest path according to sorted neighbor order wins. A known node to itself has a one-node path. An empty optional path means known endpoints with no path.
- Impact returns reverse reachability. Depth 1 means direct; greater depths mean indirect. The target is excluded through self-loops and cycles. Affected methods, class identities, and minimum-depth maps are sorted by ID. Classes come from `MethodId.declaringType`, preserving nested and location-qualified identities. Maximum depth is the maximum minimum distance, or zero for an empty affected set.

## Coverage and limits

`ResolutionCoverage` retains snapshot-wide partial scan state and scan diagnostics, partial source paths and source diagnostics (including parse/read failures), unresolved and ambiguous call sites with original failure evidence, callerless calls, resolved call-site count, and graph nodes without source definitions. Repeated lexical calls remain in coverage even though graph edges are unique. External definitions and bodies are unavailable and may hide further calls. `incomplete()` flags these observed limitations; a false value is not a guarantee of complete runtime coverage.

Impact separately returns `unresolvedCallsInAffectedMethods`, including ambiguous sites, whose known caller occurs in the observed affected method set. This scope excludes the changed target itself and unrelated callers. These calls are not proven to reach the changed target; snapshot-wide unresolved calls must never be interpreted as related impact. Coverage retains failures independently of whether any affected methods were observed.

Resolved static reachability is an observed lower bound. Missing files, missing third-party dependencies, static dispatch limits, callerless initializers, and implicit compiler behavior can hide runtime effects. No runtime dispatch expansion, reflective invocation resolution, dynamic dependency injection inference, networking, analyzed-project builds or dependency downloads are introduced.

## Verification

Focused service tests cover all seven operations, overloads and constructors, fallback IDs, deterministic ordering, immutable results, unknown/absent/isolated/external nodes, repeated calls, cycles and self-loops, minimum distances, shortest-path ties, affected-class aggregation, ambiguity evidence and scoped coverage. Integration tests derive answers from resolved synthetic commerce sources and temporary malformed/missing-symbol sources. Counting scanner, analyzer and graph-factory ports verify one analysis and reuse across repeated queries.

On 2026-10-03, `mvn -q -DskipTests compile`, focused service tests, and `mvn clean verify` passed on Java 21. Full verification reported 75 tests, zero failures, zero errors and two existing filesystem capability skips (symbolic links and POSIX access permissions). All nine new service tests ran. The library JAR was built successfully; no executable CLI acceptance check applies to this stage. `git diff --check` also passed.

## Explicit dependency metadata inputs

`new AnalysisOptions(additionalRoots, dependencyJars)` accepts immutable lists of plain `Path` values; `new AnalysisOptions(additionalRoots)` and `defaults()` continue to supply no dependency JARs. Relative dependency paths resolve against the process working directory; additional roots retain repository-relative semantics. `RepositoryAnalysisService` passes explicit JAR inputs through the source-analysis port. Only the outgoing parser adapter reads archives or constructs JavaParser/Javassist metadata. Unsupported custom analyzers reject nonempty JAR inputs rather than silently ignore them. All unresolved calls and source diagnostics remain in the returned snapshot, regardless of CLI presentation options. See [local dependency semantics and limitations](SYMBOL_RESOLUTION.md) and [CLI diagnostic/export contracts](CLI.md).
