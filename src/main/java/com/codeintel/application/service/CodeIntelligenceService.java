package com.codeintel.application.service;

import com.codeintel.application.result.*;
import com.codeintel.domain.graph.GraphTraversal;
import com.codeintel.domain.model.*;
import java.util.*;

/** Queries one snapshot; performs no source analysis or graph construction. */
public final class CodeIntelligenceService {
    private final AnalysisSnapshot snapshot;
    private final GraphTraversal traversal;
    private final List<SymbolSearchResult.Symbol> symbols;
    public CodeIntelligenceService(AnalysisSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot);
        traversal = new GraphTraversal(snapshot.graph());
        var values = new ArrayList<SymbolSearchResult.Symbol>();
        for (var source : snapshot.sources()) for (var type : source.types()) {
            values.add(new SymbolSearchResult.Symbol(type.id().value(),
                    type.simpleName().orElse(type.id().qualifiedName()), SymbolSearchResult.Kind.TYPE, type.location()));
            for (var method : type.methods()) values.add(new SymbolSearchResult.Symbol(method.id().value(),
                    method.id().name(), method.kind() == CallableKind.CONSTRUCTOR
                    ? SymbolSearchResult.Kind.CONSTRUCTOR : SymbolSearchResult.Kind.METHOD, method.location()));
        }
        values.sort(Comparator.comparing(SymbolSearchResult.Symbol::id));
        symbols = List.copyOf(values);
    }
    public SymbolSearchResult searchSymbol(String query) {
        var needle = Objects.requireNonNull(query).toLowerCase(Locale.ROOT);
        return new SymbolSearchResult(symbols.stream().filter(symbol ->
                symbol.id().toLowerCase(Locale.ROOT).contains(needle)
                || symbol.name().toLowerCase(Locale.ROOT).contains(needle)).toList());
    }
    public Optional<MethodNode> getMethod(String id) {
        return Optional.ofNullable(snapshot.methods().get(Objects.requireNonNull(id)));
    }
    public List<MethodId> findCallers(String id) { return sorted(snapshot.graph().callers(known(id))); }
    public GraphView graphView(String id, GraphViewOptions options) {
        var target = known(id);
        var selection = traversal.select(target, options.direction(), options.depth(), options.maxNodes());
        var edges = new ArrayList<com.codeintel.domain.graph.GraphEdge>();
        boolean edgeLimited = false;
        outer: for (var caller : sorted(selection.distances().keySet())) {
            for (var callee : sorted(snapshot.graph().callees(caller))) {
                if (!selection.distances().containsKey(callee)) continue;
                if (edges.size() == options.maxEdges()) { edgeLimited = true; break outer; }
                edges.add(new com.codeintel.domain.graph.GraphEdge(caller, callee));
            }
        }
        var external = new HashSet<MethodId>();
        selection.distances().keySet().stream().filter(node -> !snapshot.methods().containsKey(node.value())).forEach(external::add);
        return new GraphView(target, options, selection.distances(), edges, external,
                selection.depthLimited(), selection.nodeLimited(), edgeLimited);
    }
    public List<MethodId> findCallees(String id) { return sorted(snapshot.graph().callees(known(id))); }
    public DependencyResult findDependencies(String id) {
        var target = known(id);
        return new DependencyResult(target, ordered(traversal.reachable(target, GraphTraversal.Direction.FORWARD)));
    }
    public CallPathResult findPath(String source, String target) {
        var unknown = java.util.stream.Stream.of(source, target).map(Objects::requireNonNull)
                .filter(id -> !snapshot.graphIds().containsKey(id)).distinct().sorted().toList();
        if (!unknown.isEmpty()) throw new UnknownMethodException(unknown);
        var from = known(source); var to = known(target);
        return new CallPathResult(from, to, traversal.shortestPath(from, to));
    }
    public ChangeImpactResult analyzeChangeImpact(String id) {
        var target = known(id);
        var depths = ordered(traversal.reachable(target, GraphTraversal.Direction.REVERSE));
        var affected = List.copyOf(depths.keySet());
        var classes = affected.stream().map(MethodId::declaringType).distinct()
                .sorted(Comparator.comparing(SymbolId::value)).toList();
        var unresolved = java.util.stream.Stream.concat(snapshot.coverage().unresolvedCalls().stream(),
                snapshot.coverage().ambiguousCalls().stream())
                .filter(call -> call.caller().filter(depths::containsKey).isPresent())
                .sorted(Comparator.comparing((MethodCall call) -> call.location().path())
                        .thenComparingInt(call -> call.location().startLine())
                        .thenComparingInt(call -> call.location().startColumn())).toList();
        return new ChangeImpactResult(target,
                affected.stream().filter(method -> depths.get(method) == 1).toList(),
                affected.stream().filter(method -> depths.get(method) > 1).toList(), affected, classes, depths,
                depths.values().stream().mapToInt(Integer::intValue).max().orElse(0), snapshot.coverage(), unresolved);
    }
    private MethodId known(String id) {
        var method = snapshot.graphIds().get(Objects.requireNonNull(id));
        if (method == null) throw new UnknownMethodException(List.of(id));
        return method;
    }
    private static List<MethodId> sorted(Collection<MethodId> ids) {
        return ids.stream().sorted(Comparator.comparing(MethodId::value)).toList();
    }
    private static Map<MethodId, Integer> ordered(Map<MethodId, Integer> distances) {
        var values = new LinkedHashMap<MethodId, Integer>();
        sorted(distances.keySet()).forEach(id -> values.put(id, distances.get(id)));
        return values;
    }
}
