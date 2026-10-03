package com.codeintel.adapter.out.javaparser;

import com.codeintel.domain.model.*;
import java.util.*;

/** Qualifies duplicate signatures before call targets are indexed. */
final class DeclarationCollisions {
    private DeclarationCollisions() {}

    static List<SourceUnit> qualify(List<SourceUnit> units) {
        var groups = new HashMap<MethodId, List<MethodNode>>();
        units.forEach(u -> u.types().forEach(t -> t.methods().forEach(m ->
                groups.computeIfAbsent(m.id(), ignored -> new ArrayList<>()).add(m))));
        var replacements = new HashMap<SourceLocation, MethodId>();
        groups.values().stream().filter(g -> g.size() > 1).forEach(g -> g.forEach(m ->
                replacements.put(m.location(), new MethodId(m.id().declaringType(), m.id().name(),
                        m.id().parameterTypes(), Optional.of(m.location())))));
        if (replacements.isEmpty()) return units;
        return units.stream().map(u -> {
            var diagnostics = new ArrayList<>(u.diagnostics());
            var types = u.types().stream().map(t -> {
                var methods = t.methods().stream().map(m -> {
                    var id = replacements.get(m.location());
                    if (id == null) return m;
                    diagnostics.add(new AnalysisDiagnostic(DiagnosticSeverity.WARNING, "IDENTITY_COLLISION",
                            "Duplicate callable signature: " + m.id().value(), Optional.of(m.location()),
                            groups.get(m.id()).stream().map(MethodNode::location).toList()));
                    return new MethodNode(id, m.kind(), m.returnType(), m.parameters(), m.modifiers(), m.thrownTypes(), m.location());
                }).toList();
                return new ClassNode(t.id(), t.simpleName(), t.kind(), t.nesting(), t.enclosingType(),
                        t.modifiers(), t.supertypes(), t.fields(), methods, t.location());
            }).toList();
            var calls = u.calls().stream().map(c -> {
                var caller = c.caller().flatMap(id -> u.types().stream().flatMap(t -> t.methods().stream())
                        .filter(m -> m.id().equals(id) && contains(m.location(), c.location()))
                        .min(Comparator.comparingInt(m -> m.location().endLine() - m.location().startLine()))
                        .map(m -> replacements.getOrDefault(m.location(), id)));
                return new MethodCall(c.rawExpression(), c.name(), c.scope(), c.location(), caller,
                        c.target(), c.status(), c.failure());
            }).toList();
            return new SourceUnit(u.path(), u.packageName(), u.imports(), types, calls, diagnostics, u.partial());
        }).toList();
    }

    private static boolean contains(SourceLocation outer, SourceLocation inner) {
        return outer.path().equals(inner.path())
                && (outer.startLine() < inner.startLine() || outer.startLine() == inner.startLine() && outer.startColumn() <= inner.startColumn())
                && (outer.endLine() > inner.endLine() || outer.endLine() == inner.endLine() && outer.endColumn() >= inner.endColumn());
    }
}
