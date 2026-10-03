# CLI usage and contracts

Stage 9 was explicitly authorized on 2026-10-03, superseding the older planning restriction for CLI, packaging, tests, and associated documentation. No application or domain production code changed.

Build with JDK 21 and Maven 3.9 or newer: `mvn clean verify`. Run `java -jar target/code-intelligence.jar --help`. Maven Shade bundles runtime dependencies and supplies the manifest entry point. Failsafe runs `ExecutableJarIT` after packaging. The distribution uses the ordinary classpath, not Java's module path.

## Commands

Prefix each command below with `java -jar target/code-intelligence.jar`.

| Command | Result |
| --- | --- |
| `scan <repository>` | File, type, callable, call-site and resolution counts |
| `search <repository> <query>` | Case-insensitive symbol substring search; `''` lists all symbols |
| `method <repository> <method-id>` | Source definition, kind, location, return type and modifiers; known external targets report source-definition absence |
| `callers <repository> <method-id>` | Direct callers, sorted and deduplicated |
| `callees <repository> <method-id>` | Direct callees, sorted and deduplicated |
| `dependencies <repository> <method-id>` | Transitive dependencies and minimum distances, excluding the starting ID |
| `path <repository> <source-id> <target-id>` | Deterministic shortest path; known disconnected endpoints produce an explicit no-path result |
| `impact <repository> <method-id>` | Direct/indirect callers, affected methods/classes, minimum depths, maximum minimum depth, and unresolved calls originating in affected methods |

No arguments, `help`, `--help`, or `-h` alone show help. Help and invalid command syntax never analyze. Every analysis-bearing invocation scans, parses/resolves, and builds one graph snapshot, then queries that snapshot. No persistent index is created. Cycles terminate; same known path endpoints yield one node. Graph queries accept known external targets. Unknown IDs are errors; empty search/neighbors and disconnected known paths are successful results.

## Source roots and paths

`--source-root <path>` is repeatable and may occur anywhere before `--`, including before the command or after operands. It adds roots to standard discovery using `AnalysisOptions.additionalRoots`; it does not filter scanned files. A relative repository resolves against the process working directory. Relative source roots resolve against the repository; absolute roots must also lie inside it. Excluded directory roots are rejected. Missing roots retain scanner diagnostics. `--` ends option parsing, allowing literal queries beginning with `--`.

```powershell
java -jar target/code-intelligence.jar --source-root custom/java scan C:\projects\example --source-root generated/java
java -jar target/code-intelligence.jar search C:\projects\example -- '--literal-query'
```

## Identity quoting

Quote complete IDs with single quotes in PowerShell and POSIX shells. This protects parentheses, constructor `<init>` and nested-type `$`. IDs are exact strings; the CLI does not reinterpret or normalize them. Parameter overloads remain distinct. Prefer copying IDs from `search` rather than guessing.

The following commands work in both shells from the project directory:

```sh
java -jar target/code-intelligence.jar method src/test/resources/fixtures/commerce 'com.example.commerce.PaymentService#<init>(int)'
java -jar target/code-intelligence.jar method src/test/resources/fixtures/commerce 'com.example.commerce.NestedTypes$Worker#run(int)'
java -jar target/code-intelligence.jar path src/test/resources/fixtures/commerce 'com.example.commerce.CycleA#run()' 'com.example.commerce.CycleC#run()'
```

Canonical IDs have the form `type#method(parameter-types)`. The current extractor can retain unresolved parameter spellings even when call targets resolve, for example:

```sh
java -jar target/code-intelligence.jar method src/test/resources/fixtures/commerce 'com.example.commerce.PaymentService#pay(unresolved:6:String)@54:src/main/java/com/example/commerce/PaymentService.java:6:5-6:55'
```

Fallback IDs contain source ranges and may change after source edits or improved resolution. Stage 9 preserves the existing identity contract.

## Output and exit codes

Stdout contains deterministic summaries and query results. Stderr contains coverage limitations and diagnostic counts grouped by reason, with at most three representative examples per reason (each limited to 400 characters), plus ordinary errors without stack traces. Resolver warnings represented by failed call records are suppressed in human output so each failure is presented once. Full structured analysis results are unchanged. The executable defaults project logging to WARNING, retaining real warnings/errors; explicit JUL configuration is honored. Diagnostic multiline messages are flattened for readable records. Source ranges and paths are repository-relative; input failures can include the invalid absolute path.

`scanned` counts discovered Java files; `failed` counts units with parse/read/invalid-declaration failures, and `parsed` counts other units. Types include nested types and callables include explicit constructors. Call sites retain repeated occurrences; resolved graph edges are deduplicated. Resolution counts include resolved, unresolved and ambiguous sites, unique external targets, and sites without lexical caller identity.

| Code | Meaning |
| --- | --- |
| 0 | Completed command, including empty results, known disconnected paths, and resolution limitations |
| 1 | Fatal unexpected runtime failure |
| 2 | Invalid command syntax/options; no analysis |
| 3 | Invalid repository or analysis input, including inaccessible/non-directory repository, forbidden root, invalid dependency JAR, or diagnostics export failure |
| 4 | Unknown graph ID |
| 5 | Query completed with partial scan or partial source extraction; available results are printed |

Unknown-ID outcomes take precedence over partial-analysis success. Unresolved calls and external targets alone do not cause code 5. A malformed file does not prevent valid files from contributing results.

## Coverage limits

Resolved reachability is a lower bound. Snapshot-wide unresolved calls are not proven related to an impact target. The impact result separately identifies unresolved/ambiguous calls whose lexical callers belong to the observed affected set; even these are not proven to reach the changed target. Static calls do not prove execution. Missing third-party dependencies, omitted sources and static dispatch limitations can hide effects.

Analysis stays local. The CLI never executes analyzed code or repository builds, downloads analyzed-project dependencies, or persists an index. Runtime dispatch expansion, reflective invocation, dynamic DI, Spring analysis, networking and MCP remain outside the MVP.

## Verification

On 2026-10-03, `mvn clean verify` passed on Java 21.0.12.1 and Maven 3.9.16: 79 Surefire tests (77 passed, 2 platform-dependent tests skipped), plus 1 Failsafe test exercising 7 fresh Java processes; no failures or errors. Maven Shade reports overlapping dependency metadata and module descriptors; the distribution runs on the classpath and all packaged process checks passed.

Direct packaged smoke checks also passed for all eight commands, a constructor ID, and a nested-type ID. The commerce scan returned 13 scanned/parsed files, 0 failures, 14 types, 30 callables, 21 call sites, 20 resolved calls and 1 unresolved call, with exit 0. Scanning all included fixtures continued past `malformed/Broken.java`: 16 scanned, 15 parsed, 1 failed, with exit 5. The cycle path was `CycleA#run() -> CycleB#run() -> CycleC#run()`.

Adapter tests cover all commands, one analysis per invocation, syntax without analysis, deterministic output, roots, malformed-file continuation, unresolved visibility, cycles, constructors/nested types, external targets, unknown IDs, and exit outcomes. Packaged process tests capture both streams in files to avoid pipe deadlocks, have 45-second timeouts, and run without an additional classpath from an unrelated working directory.

## Diagnostic and dependency options (2026-10-03)

Implementation of these improvements was explicitly authorized. All flags may appear anywhere before `--`.

| Option | Contract |
| --- | --- |
| `--detailed-diagnostics` | Print every diagnostic example without truncation, each failed call once; query stdout stays unchanged |
| `--diagnostics-file <new-path>` | Save full UTF-8 JSON Lines evidence; one occurrence allowed; refuse existing destinations rather than overwrite user data |
| `--dependency-jar <path>` | Repeat once per explicitly supplied local archive; no semicolon/colon lists, wildcards, directory crawling, Maven cache discovery, or transitive dependency downloads |

Relative JAR and output paths resolve against the process working directory, on Windows and POSIX. Quote paths with spaces. Relative source roots still resolve against the analyzed repository. Source solvers take precedence, followed by explicit JARs in flag order (normalized duplicate paths are removed), followed by restricted JDK metadata. Duplicate classes in JARs use the first supplied definition. JARs need not reside inside the analyzed repository. Supply all dependencies required by the referenced types yourself. These options perform metadata analysis only: no repository builds, analyzed code execution, or annotation processing.

Missing, unreadable, non-file, malformed archives, or malformed class entries are invalid analysis inputs (exit 3). Empty valid archives are accepted but add no types. A failed export returns exit 3 and may leave an incomplete newly created file; existing files are never overwritten. Output parent directories must already exist. Query results remain successful with unresolved calls alone (exit 0); partial source/scan outcomes retain exit 5.

The saved file starts with `kind: summary`, `schemaVersion: 1`, repository, partialScan, partialSources, resolved/unresolved/ambiguous counts, and externalTargets. Subsequent lines are `scanDiagnostic`, `sourceDiagnostic`, or `call` objects, in analysis order. Each line is one JSON object. Scan diagnostics contain code, path and message. Source diagnostics contain severity, code, path, message, nullable location, and relatedLocations. Every call (including resolved sites) contains status, location, expression, name, nullable scope/caller/target, and nullable failure (category, message, competingTargets). Locations contain path, startLine, startColumn, endLine, endColumn. Lines, columns and paths retain the existing repository-relative source contract. Source diagnostics and call records intentionally preserve distinct structured evidence; a matching failure can occur in both record kinds, linked by location/code/message. Human diagnostics suppress that duplicate presentation.

```powershell
$repo = 'C:\dev\Java-Spring-RESTful-Api-JPA-Vet-Clinic-Management-System'
java -jar target/code-intelligence.jar scan $repo
java -jar target/code-intelligence.jar search $repo 'Service' --diagnostics-file '.\diagnostics-service.jsonl'
java -jar target/code-intelligence.jar scan $repo --detailed-diagnostics
# Optional: replace these paths with actual, locally available dependencies.
java -jar target/code-intelligence.jar scan $repo --dependency-jar 'C:\libraries\spring-context.jar' --dependency-jar 'C:\libraries\spring-core.jar'
$LASTEXITCODE
```

Windows PowerShell can wrap native stderr text in `NativeCommandError`, including an ordinary Java INFO record or a coverage warning. That header alone does not indicate an application failure; inspect `$LASTEXITCODE` and the actual diagnostic. Default INFO noise is now suppressed, but genuine errors and coverage warnings remain visible. `--diagnostics-file` saves JSON directly without shell stderr redirection. To configure operational logs, supply a JUL properties file before `-jar`, for example `java '-Djava.util.logging.config.file=C:\config\logging.properties' -jar target/code-intelligence.jar scan $repo`. A file containing `.level=INFO`, `handlers=java.util.logging.ConsoleHandler`, and `java.util.logging.ConsoleHandler.level=INFO` enables INFO console logs.

Lombok and other generators are a separate limitation: supplying an annotation dependency JAR does not generate missing accessors, constructors, builders, or other methods in source. No unresolved call is classified as generated without evidence. Calls and resolver failures remain available for investigation. Generated-method modeling is deferred; no DI or Spring semantic inference is included.

`SOURCE_ROOT_UNCERTAIN` describes layout discovery, not a skipped or necessarily failed file. For example `.mvn/wrapper/MavenWrapperDownloader.java` remains analyzed; package-based inference can infer the default-package directory. The warning is retained to show nonstandard layout rather than silently excluding wrapper sources.

## Improvement verification

On 2026-10-03, offline `mvn -o clean verify` passed, followed by final `mvn -o verify`: 83 Surefire tests, 81 passed and 2 existing platform-dependent scanner tests skipped; one Failsafe test passed with seven fresh packaged-JAR processes. No failures or errors. New synthetic regressions cover 600 unresolved calls with bounded default output, detailed presentation without duplicate resolver warnings, complete JSON Lines evidence, refusing overwrites, export/syntax errors, local dependency overloads/nested targets, source identity preservation and precedence, explicit ordering/duplicates/relative paths, engine classpath isolation with supplied JARs, invalid archives/class entries, Windows archive unlocking, and absent generated accessors despite a synthetic Lombok-like annotation JAR.

Read-only scan and `search ... 'Service'` smoke tests against the available vet-clinic repository both exited 0. Both retained exactly 100 scanned/parsed files, 0 failed, 100 types, 285 callables, 1,477 call sites, 914 resolved, 563 unresolved, 0 ambiguous, 67 external targets, and 2 calls without caller. No dependency JARs were supplied, so no resolution improvement is claimed. Default stderr was 11 lines / 1,877 UTF-8 bytes for each command; the old PowerShell redirected log was 821,114 bytes (different shell formatting/encoding). The new export contains 2,042 valid JSON objects including all 1,477 calls and 563 source diagnostics. The original user log's SHA-256 was unchanged. New smoke artifacts are under `target/real-repository-smoke/`; they are build-local outputs, not committed third-party fixtures.

Additional packaged checks confirmed the synthetic dependency call changes from 0 resolved / 1 unresolved to 1 resolved / 0 unresolved with an explicitly supplied JAR. Real-repository detailed mode exited 0 and contained exactly 563 failed-call examples; the final UTF-8 export parsed successfully.
