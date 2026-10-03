# Project instructions

## Current authorization

Planning only. Do not implement code, create the Maven build, or install dependencies until the user requests implementation. Documentation changes are authorized. The older specification's instruction to begin implementation is superseded by this restriction.

## Mandatory engineering rules

- Use English exclusively in responses and all project artifacts, including identifiers, comments, documentation, CLI output, diagnostics, tests, and commit messages.
- Follow SOLID principles and Clean Architecture. Dependencies point inward. Domain and application code must not depend on JavaParser, filesystem scanning implementations, CLI, or future MCP implementations.
- Use meaningful ports and adapters without speculative interfaces or unnecessary abstraction. Keep classes focused and prefer immutable models.
- Build incrementally. At each meaningful implementation stage, compile, run relevant tests, and fix failures before proceeding. Never knowingly leave broken code.
- Use Java 21, Maven, JavaParser with Symbol Solver, and JUnit 5. Start with one Maven module and an in-memory graph.
- Analyze locally. Do not upload analyzed source, execute analyzed repositories' builds, or automatically download their dependencies.
- Use AST parsing, never regex for Java syntax. Preserve unresolved calls and useful diagnostic details. A malformed file or failed call resolution must not abort analysis of other files.
- All fixtures must be synthetic or public/open-source-compatible. No proprietary assumptions or code.
- No MCP, networking, Spring-specific analysis, endpoint mapping, SQL/table analysis, runtime dispatch expansion, reflection resolution, dynamic DI inference, persistence, external databases, or cloud in the MVP.
- Keep graph construction, graph traversal, parsing, application orchestration, and presentation separate. Do not duplicate parsing or traversal logic. Use logging for core operational messages; CLI presentation may use stdout.

See [docs/ROADMAP.md](docs/ROADMAP.md) for the carried-over requirements, proposed exact structure, semantics, implementation sequence, and acceptance criteria. Changes to significant architectural decisions must be explained before implementation.
