# Stage 4 AST extraction

`SourceAnalyzer` accepts the existing `RepositorySources` and returns immutable `SourceUnit` values in input file order. `JavaParserSourceAnalyzer` reads UTF-8 source locally; its `parse(path, source)` entry point also supports in-memory input. The scanner remains responsible for discovery. Callers retain scan diagnostics and the scan partial flag alongside these per-file results; this stage does not introduce snapshot orchestration.

## Architecture and coverage

The adapter uses a fresh Java 21 parser per source and a package-private `AstExtractor` scoped to that source. AST nodes never cross the application port. This consolidates the roadmap's proposed first-pass parser, definition extractor, and initial identity mapper into two focused classes; separate resolver components remain for the second pass. No build dependencies changed.

Extraction covers package/default-package declarations, normal/static/wildcard imports, classes, interfaces, enums, records, annotations, member/local/anonymous types (including enum constant bodies), declared fields, record components represented as private final fields at their component locations, methods, annotation members, explicit constructors, compact record constructors, ordered parameters, varargs, modifiers, thrown types, and explicit supertypes. Type and call lists follow source position; member lists retain declaration order. Repeated call sites remain distinct.

Only primitive and void types are marked resolved. Reference types and type variables retain normalized AST spelling with comments and type-use annotations removed, preserving generic spelling and separate array dimensions. Varargs add one array dimension. Any unresolved parameter adds the callable declaration range to its identity, exactly as specified in DOMAIN_MODEL.md. Nested names use `$`; local/anonymous types and members of qualified types retain declaration qualifiers. No speculative import or JDK-name resolution occurs.

Method invocations, object creation, and explicit `this`/`super` invocations retain exact original expression/scope text and inclusive source ranges. LF, CRLF, CR, and tab-containing sources are supported. Extraction-only `parse(path, source)` calls have `UNRESOLVED` status and `NOT_ATTEMPTED` failure details. Repository `analyze` now attempts second-pass resolution; see [SYMBOL_RESOLUTION.md](SYMBOL_RESOLUTION.md). Lambda calls use their enclosing lexical callable. Nested type bodies form ownership boundaries: field and initializer calls have absent callers. Anonymous constructor arguments belong to the surrounding callable; body calls belong to the anonymous context. No method-reference execution, implicit constructor calls, synthesized record accessors/default constructors, or enum constant construction calls are inferred.

## Recovery and limitations

The recovery policy is deliberately conservative: an unsuccessful parser result contributes no recovered package, imports, types, or calls, even if JavaParser supplies a partial AST. The result has `partial=true` and ordered `PARSE_ERROR` diagnostics with verbose parser failure details and token ranges where available. The trustworthy recovered-data subset is empty; malformed input is never reported as fully analyzed.

Unreadable/deleted sources produce a partial unit with `SOURCE_READ_ERROR`, exception class/message, and no invented line range. The unit's path identifies the file. Expected I/O/security failures are caught per file. Model declaration-validation failures produce `INVALID_DECLARATION` partial results. Unexpected runtime failures propagate rather than being blanket-suppressed. Batch iteration continues after expected failures.

A non-partial unit means complete supported first-pass extraction, not compiler validation or resolved semantic correctness. Duplicate definition collection/rekeying, snapshot coverage, and graph construction remain later work. Stage 6 now uses discovered and package-inferred source roots for call resolution, retaining collected fallback identities. No analyzed repository builds execute and no analyzed dependencies are downloaded.

## Verification

Synthetic behavioral tests cover definitions and fallback signatures, nested constructors, annotations/generic varargs, lexical ownership, repeated/raw call text, locations and line endings, enum anonymous bodies, qualified members of local types, record components/compact constructors and Java 21 record patterns, and valid/malformed/missing-file batches with deterministic results.

Verified on 2026-10-03 with Java 21 and Maven 3.9: `mvn -o clean verify` succeeded (48 tests: 46 passed, two existing environment-dependent scanner tests skipped, zero failures/errors). All seven new extraction tests passed. `git diff --check` passed. A `jdeps` package inspection confirmed that domain/application packages do not depend on JavaParser or concrete adapters. Verification used cached engine dependencies only.
