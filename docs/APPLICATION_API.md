# Application API (stage 8)

Stage 8 is implemented with explicit user authorization. The application uses the scanner and analyzer ports and a new `CodeGraphFactory` construction port. This small boundary keeps the concrete graph adapter outside orchestration. The existing domain graph contract and BFS algorithms remain unchanged. No CLI or executable packaging is included.

## Composition and use

Concrete adapters are wired by callers outside the application layer:

```java
var analysis = new RepositoryAnalysisService(
    new FileSystemRepositoryScanner(),
    new JavaParserSourceAnalyzer(),
    InMemoryCodeGraph::fromSources);
var snapshot = analysis.analyze(repositoryPath, AnalysisOptions.defaults());
var queries = new CodeIntelligenceService(snapshot);
var symbols = queries.searchSymbol("PaymentService");
var definition = queries.getMethod("com.example.commerce.PaymentService#pay(java.lang.String)");
definition.ifPresent(method -> {
    var callers = queries.findCallers(method.id().value());
    var callees = queries.findCallees(method.id().value());
    var dependencies = queries.findDependencies(method.id().value());
    var path = queries.findPath(callers.getFirst().value(), method.id().value());
    var impact = queries.analyzeChangeImpact(method.id().value());
});
```

The example assumes a known caller exists before accessing `getFirst`. Additional source roots are passed in an immutable `AnalysisOptions` list. Scanning errors for invalid repositories propagate as `IOException`; individual scan and analysis failures remain in the snapshot. One analysis request scans, analyzes and constructs the indexed graph once. Queries use the same graph and immutable definition indexes without scanning, parsing or graph rebuilding. Graph factories must return immutable indexed graphs; the supplied in-memory adapter satisfies this contract.

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
