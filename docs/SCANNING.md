# Stage 3 repository scanning

Stage 3 was explicitly authorized on 2026-10-03. Later implementation stages still require a user request.

## Contract and boundaries

`RepositoryScanner` is an application port. `scan(repository, additionalRoots)` returns immutable `RepositorySources`: an absolute normalized repository path, repository-relative file and source-root paths, path-based diagnostics, and a partial-scan flag. The convenience overload uses no additional roots. Application code imports only Java values; filesystem operations live in `FileSystemRepositoryScanner`. Domain models are unchanged.

Scan diagnostics use the nested `RepositorySources.Diagnostic` record rather than domain `AnalysisDiagnostic`: directory failures and root uncertainty have paths but no source line ranges. No fake line numbers are assigned. Diagnostics retain exception class and message for access failures, without retaining throwable objects. Core access failures are logged with `System.Logger`.

Files, roots, and diagnostics are ordered by case-sensitive repository-relative path spelling with separators normalized to `/` for comparison. Paths themselves remain native `Path` values. Diagnostic ties are ordered by code and message. Collections are defensively copied. An unchanged filesystem and configuration produce equal results; a concurrently changing repository is not a snapshot guarantee.

## Discovery policy

- Recursively include regular files ending in lowercase `.java`, including nonstandard layouts. Source contents are never opened by the scanner.
- Skip exact directory segments `target`, `build`, `.gradle`, `.idea`, `.git`, and `node_modules` at any depth below the supplied repository. Similar names such as `targeted` remain eligible.
- Do not follow symbolic links to directories or files. A symbolic-link repository path is rejected. The supplied repository itself is the boundary, even if its directory name matches an excluded segment.
- Missing or inaccessible repository paths raise `IOException`; non-directories raise an explanatory `IOException`. Individual traversal failures produce `SCAN_ACCESS_FAILURE`, retain other discovered files, and mark the result partial.
- `SourceRootDiscovery` recognizes existing `src/main/java` and `src/test/java` directories throughout submodules, including empty directories. No build-file interpretation or build execution occurs.
- Additional roots may be relative to the repository or absolute within it. They normalize and deduplicate alongside standard roots. An escaping or excluded root is a configuration error (`IllegalArgumentException`). Roots must have been visited as directories: missing roots, files, skipped symlinks, or inaccessible roots produce `INVALID_SOURCE_ROOT` and a partial result. Explicit roots never expand the scanning boundary or override exclusions.
- Files outside standard or valid explicit roots produce `SOURCE_ROOT_UNCERTAIN`. This alone does not mark source discovery partial: the file was discovered, but its source-root interpretation remains uncertain. An explicit repository root (`Path.of(".")`) covers all discovered files.

Package-based fallback inference requires parsed package declarations and is deferred to the later AST parser stage. The scanner does not inspect Java syntax, guess from package-like directory names, or use regular expressions. Later parser/resolver integration must reconcile this uncertainty using AST data before configuring source solvers.

## Verification and limitations

Focused synthetic temporary-layout tests cover recursion, exclusions, invalid repository paths, standard and empty submodule roots, normalized and duplicate explicit roots, invalid additional roots, nonstandard layouts, stable ordering, and immutable result collections. Platform-conditional tests cover symbolic-link policy and partial access failures. Windows without symbolic-link privilege skips the symlink test; systems without POSIX permissions skip the permission-based failure test. Those skipped branches remain unverified on that host rather than being claimed as passing.

Verified on 2026-10-03 with Java 21.0.12.1 and Maven 3.9.16: production compilation succeeded; the focused scanner run completed 10 tests with zero failures/errors and two platform skips; `mvn -o clean verify` succeeded with 41 tests, zero failures/errors and the same two skips. Windows lacks POSIX permissions and the required symlink creation privilege. The library JAR was rebuilt.

`jdeps --multi-release 21 -verbose:package target/code-intelligence.jar` confirms domain dependencies remain confined to `java.base`, application packages do not depend on adapters, and the filesystem adapter depends inward on application contracts. `git diff --check` reports no whitespace errors. The existing `d6a231d` (`Initial push`) commit was present at inspection; no commit or push was created for stage 3.

All builds run offline against cached engine dependencies. No analyzed repository builds, dependencies, uploads, parser extraction, graph traversal, queries, or CLI are involved. The user's empty `src/main/java/com/codeintel/domain/model/test.txt` is preserved.
