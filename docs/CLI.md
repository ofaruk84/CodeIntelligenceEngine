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

Stdout contains deterministic summaries and query results. Stderr contains coverage, scan/source diagnostics, unresolved/ambiguous call expressions, caller IDs, failure context, and ordinary errors without stack traces. Core services also log operational messages on stderr; their default logger timestamps are not deterministic output. Diagnostic multiline messages are flattened for readable records. Source ranges and paths are repository-relative; input failures can include the invalid absolute path.

`scanned` counts discovered Java files; `failed` counts units with parse/read/invalid-declaration failures, and `parsed` counts other units. Types include nested types and callables include explicit constructors. Call sites retain repeated occurrences; resolved graph edges are deduplicated. Resolution counts include resolved, unresolved and ambiguous sites, unique external targets, and sites without lexical caller identity.

| Code | Meaning |
| --- | --- |
| 0 | Completed command, including empty results, known disconnected paths, and resolution limitations |
| 1 | Fatal unexpected runtime failure |
| 2 | Invalid command syntax/options; no analysis |
| 3 | Invalid repository or analysis input, including inaccessible/non-directory repository or forbidden root |
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
