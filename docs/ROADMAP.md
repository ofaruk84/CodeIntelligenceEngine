# Java Code Intelligence Engine: requirements and roadmap

## Current completion

Stage 10 was explicitly authorized and completed on 2026-10-04. All ten MVP stages are implemented/documented and the six acceptance questions pass from parsed synthetic sources. See [VERIFICATION.md](VERIFICATION.md) for current Java/Maven versions, full verification totals, packaged checks, platform skips and coverage limits. Stage 10 required documentation corrections only. Older planning restrictions are superseded for this authorized scope; deferred features still require explicit scope expansion. The structure below is the original proposal; actual adapter decomposition is explained in the stage documents.

## Status and inspection

The original planning inspection on 2026-09-25 found an empty `C:\dev\LegacyCodebaseInspector`. The saved project is now `C:\dev\CodebaseInspector`. The user subsequently authorized build setup and stages 2 through 7 implementation. The Maven scaffold, immutable domain models, deterministic scanner, layout-based source-root discovery, first-pass AST extraction, reusable synthetic fixture coverage, second-pass static symbol resolution, and the immutable indexed call graph with BFS traversal are implemented. See [GRAPH.md](GRAPH.md) for stage 7 contracts and verification. See [SYMBOL_RESOLUTION.md](SYMBOL_RESOLUTION.md), [FIXTURES.md](FIXTURES.md), [AST_EXTRACTION.md](AST_EXTRACTION.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md) and [SCANNING.md](SCANNING.md) for decisions and verification. Application orchestration and query services are also implemented; see [APPLICATION_API.md](APPLICATION_API.md). Stages 8 and 9 were explicitly authorized and completed. The thin CLI and executable JAR are implemented; see [CLI.md](CLI.md). These authorizations supersede planning-only wording in `AGENTS.md` for their scopes; remaining work still requires authorization.

## Product and scope

Build a generic, local-first Java engine that accepts a repository path, discovers and parses Java sources, resolves calls where possible, builds an in-memory directed graph, and exposes structured queries through a Java API and thin CLI. Deterministic local analysis should answer questions without sending entire repositories to an LLM. Future MCP tools can wrap the application API, but MCP is out of scope now.

Technology: Java 21, one Maven module, JavaParser, JavaParser Symbol Solver, and JUnit 5. Pin compatible dependency and plugin versions during implementation. No Spring Boot, databases, Kafka, Docker, cloud, authentication, billing, or other infrastructure.

## Proposed exact module and package structure

The following is the intended complete MVP structure. Build setup, package documentation, domain models, the scanner port/result, filesystem adapters, and their tests currently exist. The Maven artifact is `com.codeintel:code-intelligence`; `target/code-intelligence.jar` is an executable Java 21 distribution with bundled runtime dependencies.

```text
CodebaseInspector/
  AGENTS.md
  README.md
  pom.xml
  docs/
    ROADMAP.md
    DOMAIN_MODEL.md
  src/main/java/com/codeintel/
    bootstrap/
      Main.java
      EngineFactory.java
    domain/model/
      SymbolId.java
      MethodId.java
      TypeReference.java
      ModelChecks.java
      SourceLocation.java
      SourceUnit.java
      ImportInfo.java
      ClassNode.java
      TypeKind.java
      FieldInfo.java
      MethodNode.java
      CallableKind.java
      ParameterInfo.java
      MethodCall.java
      ResolutionStatus.java
      ResolutionFailure.java
      AnalysisDiagnostic.java
      DiagnosticSeverity.java
    domain/graph/
      CodeGraph.java
      GraphEdge.java
      GraphTraversal.java
    application/port/
      RepositoryScanner.java
      SourceAnalyzer.java
      CodeGraphFactory.java
    application/result/
      RepositorySources.java
      AnalysisSnapshot.java
      SymbolSearchResult.java
      DependencyResult.java
      CallPathResult.java
      ChangeImpactResult.java
      ResolutionCoverage.java
    application/service/
      AnalysisOptions.java
      RepositoryAnalysisService.java
      CodeIntelligenceService.java
      UnknownMethodException.java
    adapter/in/cli/
      CodeIntelCli.java
      CliArguments.java
      CliRenderer.java
    adapter/out/filesystem/
      FileSystemRepositoryScanner.java
      SourceRootDiscovery.java
    adapter/out/javaparser/
      JavaParserSourceAnalyzer.java
      JavaSourceParser.java
      SymbolResolverConfiguration.java
      DefinitionExtractor.java
      CallResolver.java
      MethodIdentityMapper.java
    adapter/out/graph/
      InMemoryCodeGraph.java
  src/test/java/com/codeintel/
    adapter/out/filesystem/RepositoryScannerTest.java
    adapter/out/javaparser/JavaSourceParserTest.java
    adapter/out/javaparser/SymbolResolutionTest.java
    domain/model/MethodIdentityTest.java
    domain/model/DomainModelTest.java
    domain/model/MethodCallTest.java
    domain/graph/CodeGraphTest.java
    application/service/RepositoryAnalysisServiceTest.java
    application/service/CodeIntelligenceServiceTest.java
    adapter/in/cli/CodeIntelCliIntegrationTest.java
  src/test/resources/fixtures/
    commerce/src/main/java/com/example/commerce/
      OrderController.java
      OrderService.java
      PaymentService.java
      InventoryService.java
      InventoryRepository.java
      NotificationService.java
      RetryPaymentJob.java
      NestedTypes.java
      UnresolvedClient.java
      CycleA.java
      CycleB.java
      CycleC.java
    malformed/Broken.java
    multi-module/
      orders/src/main/java/example/orders/Orders.java
      payments/src/main/java/example/payments/Payments.java
```

Scanner tests will create temporary directory layouts to exercise ignored folders, additional roots, invalid paths, and deterministic order. Fixture sources are analyzed as resources, not compiled as engine production sources; intentionally malformed and missing-dependency examples must remain testable.

## Architectural boundaries and rationale

The original flat scanner/parser/model/service proposal is revised to make dependency direction explicit:

- `domain` contains Java-only immutable models, a read-only graph contract, edge values, and independently testable traversal algorithms. It knows no parser, filesystem adapter, CLI, or application service.
- `application` owns scanning and source-analysis ports, orchestration, query services, and structured result types. Ports exchange plain Java/domain values, never JavaParser AST nodes or Symbol Solver objects. A `Path` value in a port is acceptable; actual filesystem operations belong in adapters.
- `adapter/out/filesystem` implements deterministic discovery. `adapter/out/javaparser` implements both analysis passes, keeping intermediate AST state private. `adapter/out/graph` implements the graph contract with Java collections and precomputed forward/reverse indexes.
- `adapter/in/cli` parses arguments, calls application services, and renders results. Future MCP would be a sibling incoming adapter.
- `bootstrap` is the composition root and the only place that wires concrete outgoing adapters into the application. `Main` delegates to the CLI and does not contain analysis logic.

There will be no dependency-injection framework or generic repository abstraction. The graph contract provides a useful boundary for storage and independent traversal tests; ordinary concrete services do not need matching interfaces. Implementations may consolidate small private helpers if warranted, but significant structural changes require explanation first.

## Analysis behavior and identities

1. Scan recursively for `.java` files in deterministic normalized relative-path order. Ignore directory segments `target`, `build`, `.gradle`, `.idea`, `.git`, and `node_modules`. Reject missing paths and non-directories cleanly. Report individual access failures without discarding already discovered sources. Do not follow directory symlinks by default.
2. Prefer standard Maven/Gradle Java roots (`src/main/java`, `src/test/java`) throughout submodules; support explicit additional roots from the first version. Discover sources even in nonstandard layouts and report source-root uncertainty. Use parsed package declarations and source locations to infer fallback roots where possible; never execute build scripts for discovery.
3. First analysis pass: parse and collect definitions, packages, imports, classes/interfaces and other supported type kinds, nested types, fields, methods, constructors, parameters, and call sites. Preserve source ranges and parsing diagnostics. Only trust recoverable AST data when sufficiently valid, marking partial extraction clearly.
4. Second pass: resolve calls with `CombinedTypeSolver`, `JavaParserTypeSolver` for discovered roots, and appropriately restricted `ReflectionTypeSolver` for JDK types. Reflection here supplies JDK type metadata; analyzing reflective runtime invocation is out of scope. Never load or execute analyzed repository classes.
5. Build a snapshot and indexed graph after resolution. One CLI invocation analyzes once and runs its requested query against that snapshot. No persisted index or automatic downloads of analyzed-project dependencies. Engine build dependency downloads are a separate Maven concern.

Canonical callable IDs use the declaring fully qualified type, callable name, and ordered parameter types, for example `com.example.PaymentService#pay(java.lang.String,java.math.BigDecimal)`. Constructors use `<init>`; member nested types use `$` in the declaring identity. Normalize varargs to array types and erase generic parameter types consistently. Constructors are callable nodes distinguished by `CallableKind`.

Fallback identities must be explicit and deterministic: if any parameter type cannot be resolved, use normalized AST type spellings with an `unresolved:` marker and append the repository-relative file path plus declaration range as a disambiguator. Such fallback IDs are stable for unchanged sources, but may change after edits or improved resolution. Local/anonymous declaring types likewise require a source-location discriminator. Duplicate canonical definitions produce diagnostics and separate location-qualified identities rather than silently overwriting definitions. Resolver target mapping must reuse collected definition identities; it must not create a second incompatible ID for the same declaration.

Each call preserves caller ID, raw expression, name, scope, source location, optional resolved target ID, resolution status, and failure category/message. Keep repeated call sites even though graph edges are deduplicated. `AMBIGUOUS` requires actual evidence of competing targets; a generic resolver exception is `UNRESOLVED`. Handle expected parser/resolver failures narrowly and avoid blanket exception suppression.

Resolved targets outside analyzed sources remain identifiable external graph targets without fabricated method definitions. Snapshot coverage distinguishes external targets, unresolved/ambiguous calls, parse failures, and partial scans. Object creation and explicit constructor invocations are included where resolvable; implicit compiler-generated calls are not synthesized. Calls in lambdas are associated with their enclosing callable and reflect lexical occurrence, not proven execution. Calls in field or initializer blocks without an enclosing callable remain visible with absent caller identity and diagnostics/coverage, without invented method edges. Method-reference execution inference is outside the MVP.

## Query contract and graph semantics

- `searchSymbol(query)`: deterministic case-insensitive substring search of type and callable names/IDs; an empty query lists all supported symbols.
- `getMethod(methodId)`: exact lookup returning an optional immutable callable definition.
- `findCallers(methodId)` / `findCallees(methodId)`: direct reverse/forward neighbors, deduplicated and sorted.
- `findDependencies(methodId)`: transitive forward reachability, excluding the starting target even through cycles; preserve minimum distances.
- `findPath(sourceMethodId, targetMethodId)`: deterministic BFS shortest call path. Same known source/target gives a single-node path. Distinguish unknown IDs from known nodes with no path.
- `analyzeChangeImpact(methodId)`: transitive reverse reachability using BFS. Direct callers have minimum distance 1; indirect callers have minimum distance greater than 1. Exclude the changed target from its own affected list, including self-loops and cycles. Return target, direct and indirect callers, affected methods/classes, minimum depth per method, maximum of those minimum depths, and resolution coverage.

An edge `A -> B` means a statically resolved call from A to B. Direct-neighbor queries may expose explicit self-edges. Query traversals mark visited nodes and handle cycles without recursion overflow. Unknown query IDs are explicit application outcomes; the CLI reports them clearly. Graph IDs can include resolved external targets even when `getMethod` has no source definition for them.

Incomplete-resolution reporting must not claim that unresolved calls are proven related to a changed target. Report snapshot-wide coverage and separately identify unresolved calls originating in the observed affected set, with their scope made explicit. Resolved graph reachability is a lower bound; missing sources/dependencies and static dispatch limits can hide additional effects. No runtime dispatch expansion, reflection resolution, dynamic dependency injection inference, or proof of runtime execution is promised.

## CLI and fixture behavior

Implemented commands: `scan`, `search`, `method`, `callers`, `callees`, `dependencies`, `path`, and `impact`, each accepting a repository path and appropriate query arguments. Allow repeatable explicit source-root options. Document shell quoting of complete method IDs. Summaries show scanned/parsed/failed files, type and callable counts, call-site counts, resolution status counts, and diagnostics. Keep summaries and query results separate from error reporting and document exit-code behavior.

The commerce fixture must derive these relationships from source:

```text
OrderController -> OrderService
OrderService -> PaymentService
OrderService -> InventoryService
InventoryService -> InventoryRepository
RetryPaymentJob -> PaymentService
CycleA -> CycleB -> CycleC -> CycleA
```

Include overloaded methods, same-class calls, calls through fields and constructor-injected dependencies, nested types, constructors, and an unresolved external call. `RetryPaymentJob` is a direct caller of `PaymentService` when it directly invokes it; the old illustrative description of it as indirect is superseded. Actual edges determine all answers.

## Implementation sequence and checkpoints

Stages 1 through 10 were explicitly authorized and completed. Stage 8 authorization supersedes the planning-only restriction for application orchestration and query services. See [APPLICATION_API.md](APPLICATION_API.md) for contracts, examples, coverage scopes and verification. Later stages start only after an explicit user request. Each meaningful stage includes compilation, relevant tests, fixes, and a short progress report. Stage 6 infers fallback roots from parsed packages when directory segments agree.

1. Set up Java 21, Maven, pinned JavaParser/Symbol Solver and JUnit 5 dependencies, compiler/test plugins, and the agreed package boundaries. Verify an initial clean build.
2. Implement immutable domain values and callable identity mapping rules, covering overloads, constructor identity, nested classes, unresolved fallback types, and collision diagnostics.
3. Implement deterministic scanning and source-root discovery. Test recursive discovery, exclusions, invalid paths, submodule roots, additional roots, and ordering.
4. Implement AST extraction and diagnostics, continuing across malformed files. Test packages/imports, fields, types, methods, constructors, parameters, nested types, and call-site locations.
5. Complete the synthetic fixture and behavioral scanner/parser tests, including partial failures and nonstandard source layouts.
6. Implement second-pass symbol resolution. Test project calls, overloads, same-class calls, dependency fields, constructors, unresolved calls, and identity consistency. Preserve evidence and failure details.
7. Implement the in-memory graph, deduplicated forward/reverse indexes, and BFS traversals. Test repeated call sites versus unique edges, self-loops, cycles, external targets, shortest paths, and minimum distances.
8. Implement analysis orchestration and all query APIs. Test structured impact output, direct/indirect classification, target exclusion, affected classes, unknown IDs, deterministic output, and coverage limitations.
9. Implement the thin CLI and executable JAR packaging. Test all commands against real parsed fixtures, error handling, and one analysis per invocation; smoke-test the packaged JAR. Completed with explicit stage 9 authorization on 2026-10-03; see [CLI usage, outcomes and verification](CLI.md). This authorization supersedes planning-only wording for this stage.
10. Completed 2026-10-04: aligned usage/API documentation, identity explanations, coverage limits and extension notes; full clean Maven verification and packaged CLI acceptance checks passed. See VERIFICATION.md for evidence and unverified platform branches.

## Assumptions and deferred capabilities

Source analysis with JDK metadata is the default. Explicit local dependency-JAR metadata support and concise/exportable CLI diagnostics were authorized and implemented on 2026-10-03; see CLI.md and SYMBOL_RESOLUTION.md. Missing third-party classpaths reduce coverage and are reported; build files are not executed. Standard main and test Java sources are included by default. Output is deterministic for an unchanged repository and the same tool configuration. No persistent index, incremental/watch mode, or multi-module Maven engine split is required.

Deferred: generated-method modeling (including Lombok, without running annotation processors), MCP/Claude integration, network servers, Spring annotations and dependency injection, endpoint mappings, JPA/SQL/table analysis, external-system inference, other programming languages, SaaS, authentication, billing, and external graph/database storage. Adding these requires an explicit scope expansion.

## Definition of done

The executable JAR and Java API answer from parsed synthetic sources: which methods exist, what a method calls, who calls it directly, who depends on it indirectly, the shortest call path between methods, and the observed impact of changing a method. All required behavioral tests pass, the project compiles on Java 21, malformed input remains diagnosable, and unresolved calls remain visible. The engine works locally, uses no proprietary data, and preserves Clean Architecture for a future MCP adapter.
