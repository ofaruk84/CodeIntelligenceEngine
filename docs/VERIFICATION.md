# Final MVP verification

Stage 10 was explicitly authorized and completed on 2026-10-04 (Europe/Istanbul). This report describes the current implementation; dated verification sections in earlier stage documents are historical evidence. No production code or build configuration changes were needed in stage 10. No commit or publication was created.

## Environment and build

Windows 11 amd64; Eclipse Temurin OpenJDK/Javac 21.0.12.1, runtime build 21.0.12.1+1-LTS; Apache Maven 3.9.16. Confirmed with `java -version`, `javac -version`, and `mvn -version`.

`mvn clean verify` succeeded: 83 Surefire tests (81 passed, 2 skipped), zero failures/errors; one Failsafe test passed, exercising seven fresh executable-JAR processes without extra classpath from an unrelated working directory. The two scanner skips cover unavailable symbolic-link privileges and POSIX permission controls on Windows. These branches are not verified on this host. Maven Shade metadata/module-descriptor overlap warnings remain; executable classpath checks passed.

Final `mvn verify` after the usage/API edits also succeeded with the same 83 Surefire tests (two skips) and one passing Failsafe test. `git diff --check` passed. Domain/application import inspection found no parser, Javassist or concrete-adapter dependencies.

Artifact: `target/code-intelligence.jar`. Build and supplementary process output is retained under `target/stage10-verification/` (ignored build output). The documented Java API example was separately compiled with `javac -cp target/code-intelligence.jar -d target/stage10-verification target/stage10-verification/ApiExample.java` and executed using `java -cp 'target/code-intelligence.jar;target/stage10-verification' ApiExample`. It returned the expected optional A -> B -> C path.

## Packaged acceptance

Fresh `java -jar target/code-intelligence.jar` invocations covered help and every supported command. The commerce fixture returned 13 scanned/parsed files, zero failed, 14 types, 30 callables, 21 call sites, 20 resolved, one unresolved, zero ambiguous, two external targets, and zero callerless calls. The intentionally missing gateway remains in human diagnostics and JSON Lines evidence. An unresolved signature component is an identity fallback, independent of whether a call to that source declaration resolves.

All six original MVP questions are answered from synthetic parsed sources by application integration tests and packaged commands; production contains no hardcoded fixture answers:

| Question | Packaged command and verified result |
| --- | --- |
| Which methods exist? | `search ... '#pay('` returns two distinct payment overload IDs; `method` retrieves the exact fallback String definition |
| What does a method call? | `callees` for the String/int payment overload includes the String payment overload (same-class delegation) |
| Who calls it directly? | `callers` for the String/int payment overload includes RetryPaymentJob.retry |
| Who depends on it indirectly? | `dependencies` for CycleA.run returns CycleB.run at distance 1 and CycleC.run at distance 2 |
| What is the shortest call path? | `path` CycleA.run to CycleC.run returns A -> B -> C |
| What is the observed change impact? | `impact` CycleA.run returns direct C, indirect B, minimum depths 1/2, affected classes B/C, maximum minimum depth 2; excludes A despite the cycle |

Additional fresh-process checks retrieved `com.example.commerce.PaymentService#<init>(int)` and `com.example.commerce.NestedTypes$Worker#run(int)`. Scanning the complete fixtures continued after malformed input: 16 scanned, 15 parsed, one failed, exit 5. Invalid command syntax exited 2, missing repository and invalid dependency JAR exited 3, and unknown symbol exited 4. Successful commands with unresolved calls exited 0. Captured stdout contains summaries/query results; stderr contains coverage evidence/errors. Full diagnostic export parsed as JSON Lines and retained all 21 call records; refusing an existing export destination exited 3. Explicit source roots and detailed diagnostics also passed.

A newly compiled synthetic local dependency JAR changed a one-call source fixture from zero resolved/one unresolved to one resolved/zero unresolved when supplied with `--dependency-jar`; both scans exited 0. Only this authored synthetic library was compiled, not any analyzed repository. Existing regressions additionally verify classpath isolation, JAR precedence/order/duplicates, invalid bytecode, archive closing, bounded versus detailed presentation, and absent generated accessors.

Counting ports in RepositoryAnalysisServiceTest and CLI integration tests verify one scan/analyze/graph construction per invocation and snapshot reuse for repeated queries. Existing graph/service/parser tests cover loops, overloads, fallback identities, shortest-path ties, unknown versus disconnected endpoints, callerless sites, external targets and scoped impact failures. No redundant tests or features were added for documentation work.

## Supplementary real repository

Read-only `scan` and `search ... 'Service'` against `C:\dev\Java-Spring-RESTful-Api-JPA-Vet-Clinic-Management-System` both exited 0: 100 parsed files, 100 types, 285 callables, 1,477 call sites, 914 resolved, 563 unresolved, zero ambiguous, 67 external targets, and two callerless calls. No dependency JAR was supplied, so no improvement is claimed. No source was copied into committed fixtures. No repository build/code execution, dependency fetching, or upload occurred. SHA-256 comparison confirmed `C:\Users\korkm\diagnostics.log` remained unchanged.

## Acceptance and limits

The MVP's six source-query acceptance requirements pass. The result is static observed reachability, not complete runtime impact. Missing sources/dependencies, unresolved calls, generated methods (including Lombok), interface dispatch, reflection, DI, initializers and implicit compiler calls may hide effects. Snapshot-wide unresolved sites are not proven related to a changed target; affected-origin failures are separately scoped. External targets have identities but no invented bodies/definitions.

POSIX examples use standard shell quoting but were not executed on this Windows host. Exit 1 is reserved for unexpected runtime failures and was not artificially triggered in a packaged process. The platform-dependent skipped scanner cases remain unverified. No DOT export/graph rendering, MCP, Spring semantics, runtime dispatch expansion, networking, persistence or other deferred capability is implemented. Extension adapters should consume the structured application API; parser types stay in the outgoing adapter, and traversal stays in the domain graph implementation.
