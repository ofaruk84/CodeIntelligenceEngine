# Graph command and rendering

Graph rendering was explicitly authorized on 2026-10-04. The command selects a bounded view of the existing immutable analysis snapshot; it scans, parses/resolves, and builds the graph exactly once. No analyzed repository builds, annotation processors, dependency downloads, or runtime code are executed.

## Usage

From the project directory, after `mvn verify`:

```powershell
java -jar target/code-intelligence.jar graph src/test/resources/fixtures/commerce 'com.example.commerce.CycleA#run()'
java -jar target/code-intelligence.jar graph src/test/resources/fixtures/commerce 'com.example.commerce.CycleA#run()' --direction callees --depth 3 --format dot
New-Item -ItemType Directory -Force target/graph-demo
java -jar target/code-intelligence.jar graph src/test/resources/fixtures/commerce 'com.example.commerce.CycleA#run()' --direction callees --depth 3 --format dot --output 'target/graph-demo/cycle.dot'
java -jar target/code-intelligence.jar graph src/test/resources/fixtures/commerce 'com.example.commerce.CycleA#run()' --direction callees --depth 3 --format svg --output 'target/graph-demo/cycle.svg'
java -jar target/code-intelligence.jar graph src/test/resources/fixtures/commerce 'com.example.commerce.CycleA#run()' --direction callees --depth 3 --format png --output 'target/graph-demo/cycle.png'
```

Java commands and single-quoted IDs also work in POSIX shells; use `mkdir -p target/graph-demo` there. Quote complete IDs and paths containing spaces. Obtain exact IDs from `search`; constructors, nested classes, overloads, and location-qualified fallback IDs are preserved without reinterpretation.

| Option | Default | Accepted values |
| --- | --- | --- |
| `--direction` | `callers` | `callers` (impact), `callees` (dependencies) |
| `--depth` | `3` | Integer `0..100`, measured in minimum BFS hops |
| `--max-nodes` | `200` | Integer `1..10000`, including the target |
| `--max-edges` | `500` | Integer `1..50000` |
| `--format` | `text` | `text`, `dot`, `svg`, `png` |
| `--output` | Standard output for text/DOT | New file path; required for SVG/PNG |

Graph flags occur anywhere before `--`, once each, and require the `graph` command. Existing analysis flags also apply, including explicit source roots, local dependency JAR metadata, and diagnostic export. Invalid bounds/formats/duplicate options return exit 2 before analysis. Unknown exact IDs return exit 4; known isolated nodes render successfully. Output/rendering failures return exit 3. Partial scan or source extraction returns exit 5 with available results. Unresolved calls alone do not change success status.

## Selection and coverage semantics

One iterative domain BFS implementation supplies all traversals, including bounded view selection. It uses existing forward/reverse graph indexes and discovers neighbors in canonical ID order. The target has depth 0; each selected node retains its minimum distance, including where multiple paths share a node. The node cap stops new discovery but does not stop checking selected nodes for omitted neighbors. The view contains every original caller-to-callee edge whose endpoints are selected, including edges crossing BFS layers, cycles, self-loops, and boundary-to-boundary edges. If the edge cap applies, the first edges in canonical caller/target order are retained; node selection is unaffected.

Arrows always mean original caller -> callee, including reverse caller exploration. Direction changes selection, not edge orientation. Depth 0 selects only the target and preserves an actual target self-loop. Self-loops and shared/cyclic references never create duplicate nodes or recursive expansion. Text uses a node inventory plus an edge/reference list; references to the root or an already referenced edge target receive a repeat marker, without claiming that repetition alone proves a cycle. Actual self-loops have an explicit cycle marker. DOT uses generated stable keys (`n0`, `n1`, ...) independent of identity punctuation. Labels and tooltip identities are escaped quoted strings, retaining overload signatures, fallback location qualifiers, constructors, and `$` nested-type identities. The target is highlighted; external targets are marked and use an ellipse.

The header/graph label reports selected node/edge counts and separate depth, node, and edge truncation flags. A flag is set when at least one neighbor or induced edge is omitted, not merely when the requested limit is reached. Truncation is successful bounded output, not a CLI failure.

Metadata, scan summaries, and snapshot-wide diagnostics go to stderr for every graph format. Standard output contains only the requested text or valid DOT; file-output invocations leave stdout empty. SVG/PNG files contain only renderer output. DOT labels and text retain a static-resolution caveat even when saved independently of stderr. Unresolved/ambiguous calls are never fabricated into edges. Resolved static reachability can miss dependencies and does not prove runtime execution, dynamic dispatch, reflection, or injected runtime wiring. Missing source definitions for known external IDs are explicitly distinguished from unknown IDs.

## Files and Graphviz

Text/DOT use only the JDK; no rendering library was added to Maven. SVG/PNG use the external Graphviz `dot` executable. This Windows machine has Graphviz **16.1.0**, installed through `winget install --id Graphviz.Graphviz --exact --source winget --silent --accept-package-agreements --accept-source-agreements --disable-interactivity`. `dot -V` reports `16.1.0 (20260904.0139)`. The installation is `C:\Program Files\Graphviz\bin\dot.exe`. Java/Maven installations were retained.

[Graphviz's official download page](https://graphviz.org/download/) documents platform packages, including Windows Package Manager. On other machines, install the platform package and ensure `dot -V` works. Set `CODEINTEL_DOT` to an executable path if needed (a path only, without embedded flags or quote characters):

```powershell
$env:CODEINTEL_DOT = 'C:\Program Files\Graphviz\bin\dot.exe'
& $env:CODEINTEL_DOT -V
```

On POSIX: `export CODEINTEL_DOT=/path/to/dot`. The adapter honors `CODEINTEL_DOT` first, then checks the standard Windows Program Files Graphviz location, then uses `dot` through PATH. Open a new terminal after installation for a refreshed PATH. Missing Graphviz leaves text/DOT usable and produces an actionable rendered-format error.

Relative output paths use the invocation's working directory. Parent directories must exist; the command creates no directory trees. All existing destinations, including symlinks and directories, are refused. There is no overwrite flag: choose a new path or remove your old artifact explicitly. UTF-8 text/DOT files have no BOM. File extensions do not select formats; `--format` does.

Outputs are staged in task-owned temporary files beside the destination, then moved without replacement after success. The command never truncates an existing destination. Graphviz receives a process argument array, not interpolated shell input, and has a 30-second timeout. Stdout is discarded and stderr is captured into a task-owned file, avoiding pipe deadlock. Nonzero exits report up to 8192 stderr bytes, decoded as UTF-8 with replacement for invalid byte sequences. Timeout/interruption triggers process termination and temporary-file cleanup. Rendering errors do not publish partial files. Filesystem permission errors, missing directories, Graphviz plugin errors, or excessive graph layouts remain explicit failures.

## Architecture and verification

`GraphViewOptions` and `GraphView` are immutable application values. `CodeIntelligenceService.graphView` combines bounded domain traversal with induced-edge selection through graph indexes. `GraphRenderer` serializes only the selected view; it performs no graph traversal, source parsing, or filesystem operations. `GraphOutputWriter` owns staged filesystem output; `GraphvizProcess` owns executable discovery and bounded external execution. Domain/application packages depend on neither Graphviz nor rendering adapters.

Behavioral tests cover deterministic BFS selection and minimum distances, direction/orientation, all bounds and truncation, self-loops/cycles/shared nodes, overloads/nested/constructor/fallback identities, external and isolated nodes, unknown nodes, escaping, text repeat markers, invalid flags, output paths/refused overwrites, one analysis per invocation, nonzero renderer failures, large stderr, and timeout cleanup. Failsafe's `GraphExecutableJarIT` analyzes synthetic source through the packaged JAR, checks pure DOT export and relative paths with spaces, and renders SVG/PNG with Graphviz. Unavailable Graphviz causes an explicit JUnit assumption skip for the rendered integration test; text/DOT tests always run. Cross-platform process-error simulation is Windows-specific and is explicitly skipped elsewhere.

Generated demo artifacts live under ignored `target/graph-demo/`; none are source-controlled. See [VERIFICATION.md](VERIFICATION.md) for the build record.
