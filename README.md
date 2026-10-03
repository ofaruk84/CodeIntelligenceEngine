# Java Code Intelligence Engine

Local Java source analysis engine with a thin CLI and executable JAR. See [CLI usage and outcomes](docs/CLI.md), [scanner contracts and limitations](docs/SCANNING.md), [extraction coverage and recovery policy](docs/AST_EXTRACTION.md), [symbol resolution and coverage limits](docs/SYMBOL_RESOLUTION.md), [graph contracts](docs/GRAPH.md), and [application API](docs/APPLICATION_API.md).

## Requirements and build

- JDK 21 (Maven must run with Java 21)
- Apache Maven 3.9 or newer

From the project directory:

```powershell
java -version
javac -version
mvn -version
mvn clean verify
```

The build pins JavaParser/Symbol Solver 3.28.2 and JUnit Jupiter 5.14.4, compiles with Java release 21, and runs JUnit 5 tests with Surefire. Symbol Solver brings in the matching JavaParser core dependency. Verification tests parse Java 21 record patterns, resolve JDK methods, and resolve calls across synthetic local source files.

Maven downloads the engine's own build dependencies on the first build. Analyzed repositories will not have their builds executed or dependencies downloaded automatically.

The build produces `target/code-intelligence.jar`, bundling runtime dependencies with `com.codeintel.bootstrap.Main` as its entry point. Run it with Java 21; no extra classpath or IDE is needed. `mvn test` runs adapter/service/domain tests; `mvn verify` also runs fresh-process JAR tests after packaging.

```powershell
java -jar target/code-intelligence.jar --help
java -jar target/code-intelligence.jar scan src/test/resources/fixtures/commerce
java -jar target/code-intelligence.jar search src/test/resources/fixtures/commerce '#pay('
java -jar target/code-intelligence.jar impact src/test/resources/fixtures/commerce 'com.example.commerce.CycleA#run()'
```

Use the exact IDs returned by `search`, including fallback location qualifiers. Each invocation analyzes once in memory. Default diagnostics are summarized; use `--detailed-diagnostics` for full human evidence or `--diagnostics-file <new-path>` for JSON Lines. Repeat `--dependency-jar <path>` for explicit local dependencies; no project dependency downloads or annotation processors run. See [all commands, quoting, exit codes, and verification](docs/CLI.md).

Repository analysis canonicalizes resolvable parameter signatures before publishing definitions and graph references. For generated methods, analyze a separate prepared source tree, or supply compiled project metadata while analyzing callers without overlapping source declarations. See [prepared generated-method inputs](docs/CLI.md#prepared-generated-method-inputs); annotation JARs alone do not generate methods.

## Package boundaries

- `domain.model`, `domain.graph`: parser-independent domain values and graph behavior.
- `application.port`, `application.result`, `application.service`: application contracts, results, and orchestration.
- `adapter.out.filesystem`, `adapter.out.javaparser`, `adapter.out.graph`: concrete infrastructure adapters.
- `adapter.in.cli`: command-line presentation.
- `bootstrap`: composition root.

JavaParser types belong in the parser adapter and do not leak into domain or application APIs. `EngineFactory` wires concrete outgoing adapters; `Main` delegates to the CLI. The CLI parses, invokes existing services, and renders results.

## Current acceptance

Stage 10 usage documentation and final verification completed on 2026-10-04. See [current verification and limitations](docs/VERIFICATION.md) for test totals, packaged checks and source-derived acceptance answers. The graph exposes structured queries; DOT export and graph rendering are not implemented.

The build commands above work in PowerShell and POSIX shells. Check the Java version reported by Maven as well as by the Java launcher. For compilation alone use `mvn compile`; for unit tests use `mvn test`; for packaging use `mvn package`; for complete acceptance including executable-JAR integration tests use `mvn clean verify`.
