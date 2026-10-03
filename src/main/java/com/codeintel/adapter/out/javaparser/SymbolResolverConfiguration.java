package com.codeintel.adapter.out.javaparser;

import com.codeintel.application.result.RepositorySources;
import com.codeintel.domain.model.SourceUnit;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import java.nio.file.Path;
import java.util.*;

/** Source-only solver configuration; never imports the engine dependency classpath. */
final class SymbolResolverConfiguration {
    static ParserConfiguration create(RepositorySources sources, List<SourceUnit> units) {
        var roots = new TreeSet<Path>(Comparator.comparing(Path::toString));
        sources.sourceRoots().forEach(root -> roots.add(sources.repository().resolve(root)));
        for (var unit : units) {
            if (unit.partial()) continue;
            Path parent = sources.repository().resolve(unit.path()).getParent();
            String[] segments = unit.packageName().isEmpty() ? new String[0] : unit.packageName().split("\\.");
            boolean matches = true;
            for (int i = segments.length - 1; i >= 0; i--) {
                if (parent == null || !parent.getFileName().toString().equals(segments[i])) { matches = false; break; }
                parent = parent.getParent();
            }
            if (matches && parent != null && parent.startsWith(sources.repository())) roots.add(parent);
        }
        var solver = new CombinedTypeSolver();
        var parsing = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21).setTabSize(1);
        roots.forEach(root -> solver.add(new JavaParserTypeSolver(root, parsing)));
        solver.add(new ReflectionTypeSolver(SymbolResolverConfiguration::isJdkType));
        return new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21).setTabSize(1)
                .setSymbolResolver(new JavaSymbolSolver(solver));
    }
    private static boolean isJdkType(String name) {
        // The platform loader cannot see application dependencies; metadata lookup never initializes classes.
        String candidate = name;
        while (true) {
            try {
                Class<?> type = Class.forName(candidate, false, ClassLoader.getPlatformClassLoader());
                return type.getModule().isNamed() && (type.getModule().getName().startsWith("java.")
                        || type.getModule().getName().startsWith("jdk."));
            } catch (ClassNotFoundException absent) {
                int separator = candidate.lastIndexOf('.');
                if (separator < 0) return false;
                candidate = candidate.substring(0, separator) + "$" + candidate.substring(separator + 1);
            }
        }
    }
}
