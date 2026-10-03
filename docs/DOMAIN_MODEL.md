# Stage 2 domain model contract

This document records the stage's design and historical verification. Later-stage/deferred statements describe that stage's original scope; consult [CLI.md](CLI.md), [APPLICATION_API.md](APPLICATION_API.md), and [VERIFICATION.md](VERIFICATION.md) for current behavior and final acceptance.

Stage 2 implements Java-only immutable values in `com.codeintel.domain.model`. The existing Java 21, Maven, JavaParser/Symbol Solver, and JUnit 5 build is preserved. No source-analysis adapter, graph algorithm, application query, or CLI implementation is introduced.

## Structure and decisions

All public values are records or enums. Lists and sets are defensively copied with `List.copyOf` and `Set.copyOf`, including rejection of null elements. Optional values must use `Optional.empty()` rather than null. Lists retain input order and repeated values; sets do not promise iteration order. Presentation code must sort sets when deterministic display is needed.

| Value | Responsibility |
| --- | --- |
| `SymbolId` | Canonical or location-qualified type identity |
| `MethodId` | Declaring type, callable name, ordered parameter identities, optional declaration qualifier |
| `TypeReference` | Erased semantic base name or unresolved AST spelling, array dimensions, resolution flag |
| `SourceLocation` | Repository-relative path and inclusive, one-based source range |
| `SourceUnit` | Package, imports, flat type list, call-site list, diagnostics, partial-extraction flag |
| `ImportInfo` | Qualified import name plus static/on-demand flags and location |
| `ClassNode`, `TypeKind` | Type definition, kind, nesting, enclosing ID, modifiers, supertypes, fields, callables |
| `FieldInfo`, `ParameterInfo` | Name, semantic type, modifiers, location; parameters also retain varargs syntax |
| `MethodNode`, `CallableKind` | Method/constructor definition, return type, ordered parameters, modifiers, thrown types, location |
| `MethodCall`, `ResolutionStatus` | One lexical call site and its resolution outcome |
| `ResolutionFailure` | Category, message, and competing target evidence when ambiguous |
| `AnalysisDiagnostic`, `DiagnosticSeverity` | Structured diagnostic with primary and related source locations |

`TypeReference` is the only additional public top-level value beyond the roadmap's proposed model list. It keeps semantic inputs explicit and prevents domain code from needing Java syntax parsing. Package-private `ModelChecks` shares small validation rules without creating an abstraction layer. `ClassNode.Nesting` distinguishes top-level, member, local, and anonymous declarations independently of their Java type kind.

`SourceUnit.types` is flat, including nested types. Each `ClassNode` owns its fields and callable definitions. It links to its enclosing type by `SymbolId`, not by object reference. Calls occur in `SourceUnit.calls`, not in a second list on each method. This preserves field/initializer calls with absent callers and avoids cyclic mutable graphs or duplicated call storage.

These values represent extracted data; constructing a value does not parse, scan, resolve, collect, or build a graph. Model validation checks consistency of inputs, not all Java language rules. The later parser adapter must emit diagnostics for malformed declarations and retain other usable data rather than letting a model-construction error abort the repository analysis.

## Canonical identities

The canonical method form is:

```text
declaring.type#name(ordered,parameter,types)
```

Examples:

```text
com.example.PaymentService#pay(java.lang.String,java.math.BigDecimal)
com.example.PaymentService#pay(java.math.BigDecimal,java.lang.String)
com.example.Outer$Inner#<init>(java.lang.String)
com.example.PaymentService#notify(java.lang.String[])
```

Parameter order, array rank, callable name, and declaring type participate in identity. Parameter names, return types, modifiers, and thrown types do not. Constructors use `<init>` and have no return type; methods always have a return type, including `TypeReference.resolved("void")` for void methods. `MethodNode` validates that its kind, parameter types, and optional location qualifier agree with its `MethodId`.

The future identity mapper supplies semantic names:

- Resolved reference types use fully qualified names; member nested types use `$`.
- Generic types are erased before constructing `TypeReference`: both `List<String>` and `List<Integer>` supply `java.util.List`.
- Type variables supply their erased bounds; unbounded variables supply `java.lang.Object`. Generic syntax is never parsed or erased by the domain.
- Base name and array rank are separate. `TypeReference.resolved("java.lang.String").asArray()` is `java.lang.String[]`.
- For varargs, the mapper adds one dimension to the component type exactly once. `ParameterInfo.varargs` preserves the source distinction while the identity matches the corresponding array overload. Only the last parameter may be varargs.

Usage with semantic inputs:

```java
var owner = SymbolId.canonical("com.example.PaymentService");
var id = MethodId.canonical(owner, "pay", List.of(
        TypeReference.resolved("java.lang.String"),
        TypeReference.resolved("java.math.BigDecimal")));
```

The records retain structured identity components. `value()` and `toString()` render IDs; this stage does not implement string-to-ID parsing or a persistence format.

## Fallbacks and source locations

If any parameter is unresolved, its `TypeReference` contains a normalized AST base-type spelling with `resolved=false`. The adapter owns AST normalization: strip comments and type-use annotations, use its consistent AST rendering, and represent outer array/varargs dimensions separately. The domain treats this spelling as opaque and rejects blank, untrimmed, or control-character-containing values; it does not resolve or parse it.

An unresolved component renders as `unresolved:<length>:<spelling>` plus array dimensions. Its length prefix prevents punctuation inside the spelling from colliding with signature separators. Every callable containing an unresolved parameter must include its declaration location, even when its owner is already location-qualified.

```java
var declaration = new SourceLocation("src/PaymentService.java", 4, 1, 6, 2);
var fallback = new MethodId(owner, "pay",
        List.of(TypeReference.unresolved("Missing<T>")),
        Optional.of(declaration));
```

Result:

```text
com.example.PaymentService#pay(unresolved:10:Missing<T>)@23:src/PaymentService.java:4:1-6:2
```

Locations render as `<path-length>:<path>:<start-line>:<start-column>-<end-line>:<end-column>`. Lengths use Java `String.length()` (UTF-16 code units). Paths normalize backslashes to `/` without accessing the filesystem. Absolute paths, drive-qualified paths, empty/dot/parent segments, and control characters are rejected. Case is preserved; scanner-stage policy must supply consistent repository-relative casing and spelling.

Fallback identities are deterministic for unchanged semantic inputs, source paths, and declaration ranges. They may change after moving files, editing declaration ranges, changing AST normalization, or improving resolution. No cross-edit identity stability is promised.

## Local, anonymous, nested, and duplicate types

Member identities use `Outer$Inner`. Local and anonymous types must use `SymbolId.at(declarationLocation)`; `ClassNode` rejects unqualified IDs for those nesting categories. Local/anonymous base names are adapter-supplied labels, not inferred compiler numbering. Named types have a simple name; anonymous classes have none.

A member of a location-qualified enclosing type also requires its own declaration qualifier. This prevents same-named member types inside different local classes or duplicate enclosing declarations from merging. Callables retain their full declaring `SymbolId`, including any qualifier.

When collection later detects duplicate canonical declarations, it must:

1. Keep all declarations, rather than overwrite a map entry.
2. Give each conflicting definition its own location-qualified ID. Qualify member types when their enclosing type becomes qualified.
3. Create `AnalysisDiagnostic.duplicateDeclaration(canonicalIdentity, firstLocation, conflictingLocation)` with both locations preserved.
4. Map resolver targets back to those collected identities. Do not independently reconstruct an incompatible target ID or arbitrarily select a conflicting definition.

`SymbolId.at`, `MethodId.at`, and the diagnostic factory support that policy. Detection, rekeying, and resolver mapping belong to later stages; no collector exists yet. Lists intentionally retain duplicate entries so the value layer cannot silently discard evidence.

## Calls and diagnostics

`MethodCall` preserves raw expression text, lexical name, optional scope, source location, optional caller, optional target, resolution status, and failure details. The lexical name can be a method name, constructor type name, `this`, or `super`; it need not match the canonical target's `<init>` name. Raw expression and scope text are not rewritten.

| Status | Target | Failure |
| --- | --- | --- |
| `RESOLVED` | Required | Absent |
| `UNRESOLVED` | Absent | Required, with no competing targets |
| `AMBIGUOUS` | Absent | Required, with at least two distinct competing target IDs |

The value layer verifies the shape of ambiguity evidence. The resolver adapter remains responsible for supplying actual competing candidates rather than guesses. Generic resolver exceptions are unresolved failures, not evidence of ambiguity. A first-pass, not-yet-resolved call can use an unresolved failure category such as `NOT_ATTEMPTED`.

Calls without an enclosing callable retain `Optional.empty()` callers. Coverage/diagnostics for those sites are a later orchestration responsibility. Repeated calls at different ranges remain distinct; the source list does not deduplicate even equal values. Graph-edge deduplication is deferred. External target IDs are allowed without fabricating source definitions. Method-reference execution and implicit compiler-generated calls are not modeled as inferred calls.

Diagnostic codes and failure categories are nonblank strings so future adapters can report useful categories without importing parser exceptions into the domain. Exception messages can be retained as text; no mutable throwable objects are stored. Diagnostic primary locations are optional, and related locations may point into other files. `SourceUnit` validates that its definitions, call sites, and diagnostic primary locations belong to its own file. `partial=true` represents incomplete extraction and can accompany an empty definition list.

## Verification

Verified on 2026-10-03 with Temurin Java 21.0.12.1 and Maven 3.9.16:

- `mvn -o clean verify`: BUILD SUCCESS; 31 tests, zero failures, errors, or skipped tests. This includes 29 domain tests and the two existing JavaParser setup tests.
- `target/code-intelligence.jar` was rebuilt successfully as a library artifact.
- `jdeps --multi-release 21 -verbose:package target/code-intelligence.jar` reports only `java.base` dependencies, including for `com.codeintel.domain.model`.
- All verification ran offline using cached engine dependencies. No build configuration changes or dependency installation were needed.

The tests cover defensive copying and unmodifiable accessors, overload ordering, constructor/member identities, varargs/array equivalence, erased semantic inputs, fallback stability and delimiter safety, local/anonymous/member qualifiers, duplicate diagnostics, call-site retention, absent callers, external targets, and invalid resolution/model combinations.

Run the complete build with cached engine dependencies:

```powershell
mvn -o clean verify
jdeps --multi-release 21 -verbose:package target/code-intelligence.jar
```

No analyzed-repository build or dependency download is involved. Stage 2 does not claim parser extraction, resolution coverage, graph query, or CLI acceptance; those belong to later stages.
