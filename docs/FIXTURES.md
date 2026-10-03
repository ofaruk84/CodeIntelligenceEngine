# Stage 5 fixture and behavioral coverage

Stage 5 implementation was explicitly authorized on 2026-10-03. No production code or build configuration changes were needed. Stage 4 extraction was complete before this work began.

See [fixture layout and source relationships](../src/test/resources/fixtures/README.md). Sixteen Java resource files cover commerce main/test roots, two modules, and isolated malformed syntax. These input files remain outside Maven compilation roots.

`SyntheticFixtureTest` scans resource directories and invokes the public analyzer. Assertions exercise package/import/type/field/constructor/parameter extraction, distinct overload and nested identities, constructor injection, collaborator and same-class call text, repeated sites, lexical caller association, unresolved evidence, exact inclusive source ranges checked against file contents, deterministic results, and valid extraction in a malformed batch. Tests do not assume resolved targets or graph reachability.

Existing scanner tests cover recursive discovery, exact ignored directory segments at multiple depths, normalized deterministic ordering, invalid repositories and explicit roots, standard main/test and empty roots throughout submodules, additional-root normalization/deduplication, symlinks, and partial access failures. Two additional temporary-directory tests cover an empty repository and a mixed standard/custom/loose layout: additional roots suppress uncertainty only for covered files and do not restrict discovery. Root uncertainty is a warning without a partial scan; invalid additional roots make the scan partial while preserving files.

Package-based fallback root inference remains deferred. Source-root discovery uses layouts and explicit roots only. Missing external dependencies remain intentionally unresolved, and no resolution or graph assertions are part of this stage. Malformed recovery follows the current conservative contract: no recovered definitions/calls, partial=true, structured PARSE_ERROR diagnostics. OS-specific symlink/permission tests can skip when facilities are unavailable.

## Verification

Verified on 2026-10-03 using Java 21 and Maven 3.9 with cached engine dependencies only:

- `mvn -o -Dtest=RepositoryScannerTest,SyntheticFixtureTest test`: passed after correcting test expectations to match public import accessors and the semicolon retained in explicit constructor invocation text.
- `mvn -o clean verify`: passed, 54 tests, 52 passed, two existing environment-dependent scanner skips, zero failures/errors. All six new tests passed. The skips cover unavailable Windows symlink creation and POSIX permission controls.
- `git diff --check`: passed.

No analyzed repository build was executed and no dependencies were downloaded.
