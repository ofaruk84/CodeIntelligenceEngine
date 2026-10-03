package com.codeintel.adapter.out.javaparser;

import com.codeintel.application.result.RepositorySources;
import com.codeintel.domain.model.SourceUnit;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import java.nio.file.Path;
import java.util.*;

/** Explicit local solver configuration; never imports the engine dependency classpath. */
final class SymbolResolverConfiguration {
    static ParserConfiguration create(RepositorySources sources, List<SourceUnit> units, List<Path> dependencyJars) {
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
        // Source definitions take precedence; explicit JAR order is retained, duplicates removed.
        var jars = new LinkedHashSet<Path>();
        dependencyJars.forEach(path -> jars.add(path.toAbsolutePath().normalize()));
        var pool = new javassist.ClassPool(false);
        // Metadata only from the bootstrap JDK resource context, never the engine classpath.
        pool.appendClassPath(new javassist.ClassClassPath(Object.class));
        var jarDeclarations = new ArrayList<List<javassist.CtClass>>();
        for (var jar : jars) {
            if (!java.nio.file.Files.isRegularFile(jar) || !java.nio.file.Files.isReadable(jar))
                throw new IllegalArgumentException("Dependency JAR is not a readable regular file: " + jar);
            var declarations = new ArrayList<javassist.CtClass>();
            // Eager bytecode metadata avoids JarTypeSolver's uncloseable cached URL archive handles.
            try (var archive = new java.util.jar.JarFile(jar.toFile(), true,
                    java.util.zip.ZipFile.OPEN_READ, Runtime.version())) {
                for (var entry : archive.versionedStream().filter(e -> !e.isDirectory()
                        && e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")
                        && !e.getName().equals("module-info.class")).sorted(Comparator.comparing(java.util.jar.JarEntry::getName)).toList()) {
                    try (var input = new java.io.DataInputStream(archive.getInputStream(entry))) {
                        var metadata = new javassist.bytecode.ClassFile(input);
                        var known = pool.getOrNull(metadata.getName());
                        declarations.add(known != null ? known : pool.makeClass(metadata));
                    }
                }
            } catch (java.io.IOException | RuntimeException failure) {
                throw new IllegalArgumentException("Invalid dependency JAR " + jar + ": " + failure.getMessage(), failure);
            }
            jarDeclarations.add(declarations);
        }
        for (var declarations : jarDeclarations) {
            var jarSolver = new MemoryTypeSolver();
            solver.add(jarSolver);
            for (var declaration : declarations) {
                var type = com.github.javaparser.symbolsolver.javassistmodel.JavassistFactory.toTypeDeclaration(declaration, solver);
                jarSolver.addDeclaration(declaration.getName().replace('$', '.'), type);
            }
        }
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
