# Java Code Intelligence Engine

Local Java source analysis engine. Build setup, immutable domain models, deterministic repository scanning, layout-based source-root discovery, AST extraction, static symbol resolution, and the immutable indexed call graph with BFS traversal are complete. See [scanner contracts and limitations](docs/SCANNING.md), [extraction coverage and recovery policy](docs/AST_EXTRACTION.md), [symbol resolution and coverage limits](docs/SYMBOL_RESOLUTION.md), and [graph contracts and traversal semantics](docs/GRAPH.md). Application query services are implemented; see [API examples and coverage semantics](docs/APPLICATION_API.md). The CLI remains planned in [the roadmap](docs/ROADMAP.md).

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

The build pins JavaParser/Symbol Solver 3.28.2 and JUnit Jupiter 5.14.4, compiles with Java release 21, and runs JUnit 5 tests with Surefire. Symbol Solver brings in the matching JavaParser core dependency. Initial verification tests parse Java 21 record patterns, resolve JDK methods, and resolve calls across synthetic local source files.

Maven downloads the engine's own build dependencies on the first build. Analyzed repositories will not have their builds executed or dependencies downloaded automatically.

The build produces `target/code-intelligence.jar`. It is currently a library scaffold, not an executable CLI or a bundled distribution.

## Package boundaries

- `domain.model`, `domain.graph`: parser-independent domain values and graph behavior.
- `application.port`, `application.result`, `application.service`: application contracts, results, and orchestration.
- `adapter.out.filesystem`, `adapter.out.javaparser`, `adapter.out.graph`: concrete infrastructure adapters.
- `adapter.in.cli`: command-line presentation.
- `bootstrap`: composition root.

Package documentation establishes the boundaries; production engine classes are added in subsequent roadmap stages. JavaParser types belong in the parser adapter and must not leak into domain or application APIs.
