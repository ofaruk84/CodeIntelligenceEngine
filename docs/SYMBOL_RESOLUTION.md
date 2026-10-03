# Stage 6 symbol resolution

This document records the stage's design and historical verification. Later-stage/deferred statements describe that stage's original scope; consult [CLI.md](CLI.md), [APPLICATION_API.md](APPLICATION_API.md), and [VERIFICATION.md](VERIFICATION.md) for current behavior and final acceptance.

`JavaParserSourceAnalyzer.analyze(RepositorySources)` now collects all definitions before resolving calls. It retains successfully extracted compilation units privately, attaches the configured solver, and returns immutable `SourceUnit` values in scanner order. Original ASTs are reused for extraction and call resolution; JavaParserTypeSolver may independently parse source declarations for its own source lookup cache. `parse(path, source)` remains an extraction-only convenience and reports `NOT_ATTEMPTED` calls because it has no repository/source-root context. No parser types cross the application port.

## Solver configuration

`SymbolResolverConfiguration` uses the scanner's standard Maven/Gradle roots across submodules and explicit additional roots. It also infers a fallback root when parsed package segments match the file's parent directories. It never guesses a mismatched package layout. Roots are deduplicated and ordered, and JavaParserTypeSolver uses Java 21 and a tab size of one, matching extraction ranges.

CombinedTypeSolver uses source solvers first, explicitly supplied local dependency metadata next, and a restricted ReflectionTypeSolver last. The reflection predicate verifies that a type is visible through the platform class loader and belongs to a named `java.*` or `jdk.*` module. Metadata lookup does not initialize classes. This is stricter than merely allowing `java`/`javax` names and prevents engine dependencies from becoming an analyzed-project classpath. Nested JDK types are supported. No repository build runs, analyzed classes execute, or project dependencies are downloaded. Local dependency JAR metadata is read only when explicitly supplied. Scanner diagnostics and its partial flag remain the caller's responsibility until snapshot orchestration.

## Target identities and preserved evidence

`CallResolver` indexes collected callable definitions by repository-relative file path and exact declaration range. A resolved source declaration maps back to that identity, including overloads, constructors named `<init>`, member types named with `$`, and fallback/location-qualified IDs. First-pass reference parameter spellings and fallback IDs are intentionally retained; improved call resolution does not rekey definitions or callers. Generic and varargs source targets reuse the existing definition's normalization.

Targets without a collected definition receive a canonical ID from their static declaring type, callable name, and erased parameter types. Arrays retain their dimensions; varargs are already arrays in solver declarations. Nested external owners use `$`. No source definition is fabricated for JDK calls or implicit source constructors/accessors. Static interface declarations remain the target even when runtime implementations exist.

Every original occurrence retains its expression, scope, range, optional caller, and ordering. Repeated calls are retained separately. Missing symbols and known solver limitations produce `UNRESOLVED` calls with exception class/message and a warning diagnostic at the occurrence. Parse/read diagnostics and partial units survive unchanged. Expected unsolved symbols, unsupported solver operations, solver state/argument failures, generic conflicts, ambiguity reports, and solver parse problems are isolated per call; unexpected runtime defects propagate. No blanket catch hides programming defects.

Solver ambiguity exceptions supply a message rather than structured competing target IDs. They remain `UNRESOLVED` with that detail. This stage does not claim `AMBIGUOUS` without independently verified competing-target evidence.

## Coverage limits

This is static source analysis with JDK metadata, not compiler validation or a runtime call graph. Missing third-party dependencies, unsupported JavaParser cases, malformed source, inconsistent layouts, and implicit declarations reduce coverage. Malformed files retain the conservative stage 4 policy: no recovered definitions or calls. Package inference supports directory/package agreement only. Source-only resolution follows the solver's deterministic root order and does not infer build-module dependency visibility. There is no runtime dispatch expansion, dynamic DI inference, reflective invocation inference, method-reference execution inference, or synthesized compiler call sites. Graph construction, queries, CLI, and snapshot coverage remain later stages.

## Behavioral validation

Six `SymbolResolutionTest` cases cover commerce collaborator/field calls, overloads, same-class/static calls, constructor chains and nested constructors, static interface declaration targets, generic/varargs identities, nested external types, repeated calls, initializer calls without callers, submodule/test/explicit/package-inferred roots, malformed files, missing external dependencies, runtime classpath isolation, external targets without fabricated definitions, ambiguity isolation, and deterministic results. Existing fixture assertions now accept attempted resolution while retaining first-pass extraction checks.

Verified on 2026-10-03 using Java 21 and Maven 3.9: focused symbol/fixture tests passed, then `mvn -o clean verify` succeeded (60 tests: 58 passed, two existing environment-dependent scanner tests skipped, zero failures/errors). All six symbol-resolution tests passed. `jdeps -verbose:package target/code-intelligence.jar` confirmed that domain/application packages depend only on Java and inward project packages. `git diff --check` passed after whitespace cleanup. Verification used cached engine dependencies only.

## Explicit local dependency metadata

`AnalysisOptions(additionalRoots, dependencyJars)` carries immutable plain `Path` values. The original single-list constructor and source-analysis method remain available. The application passes the JAR list through `SourceAnalyzer.analyze(sources, dependencyJars)`; adapters that do not support it reject nonempty inputs explicitly. Domain/application code stays independent of JavaParser and Javassist.

The outgoing parser adapter normalizes and deduplicates JAR paths preserving input order, opens each archive with Java 21 multi-release selection, reads bytecode metadata into a private Javassist pool, and registers JavaParser `MemoryTypeSolver` declarations via `JavassistFactory`. It closes every archive and entry stream before resolving calls. This replaces the initially tested `JarTypeSolver` mechanism because its cached URL archive handles prevented moving supplied JARs on Windows after completed analysis. No global URL-cache or Javassist settings are changed. Source solvers still have priority and existing first-pass definition IDs remain unchanged; resolved dependency targets are external IDs without fabricated source definitions. Metadata loading increases memory use with supplied JAR size. Explicit JARs are treated as classpath metadata, without build-module or module export visibility inference.

Adding Lombok's annotation JAR cannot materialize generated source methods. Annotation processors are never executed. Preserve unresolved evidence and treat generated-method modeling as a future capability separate from generic dependency metadata.
