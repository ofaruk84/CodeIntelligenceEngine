package com.codeintel.adapter.out.filesystem;

import com.codeintel.application.port.RepositoryScanner;
import com.codeintel.application.result.RepositorySources;
import com.codeintel.application.result.RepositorySources.Diagnostic;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Does not follow symbolic links, open Java content, or execute build scripts. */
public final class FileSystemRepositoryScanner implements RepositoryScanner {
    static final Comparator<Path> PATH_ORDER = Comparator.comparing(path -> path.toString().replace('\\', '/'));
    private static final Set<String> EXCLUDED = Set.of("target", "build", ".gradle", ".idea", ".git", "node_modules");
    private static final System.Logger LOG = System.getLogger(FileSystemRepositoryScanner.class.getName());

    @Override
    public RepositorySources scan(Path repository, List<Path> additionalRoots) throws IOException {
        Path base = Objects.requireNonNull(repository, "repository").toAbsolutePath().normalize();
        Objects.requireNonNull(additionalRoots, "additionalRoots");
        BasicFileAttributes attributes = Files.readAttributes(base, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory()) throw new IOException("Repository path must be a non-symlink directory: " + base);
        var explicit = new ArrayList<Path>();
        for (Path root : additionalRoots) {
            Path absolute = base.resolve(Objects.requireNonNull(root, "additional root")).normalize();
            if (!absolute.startsWith(base)) throw new IllegalArgumentException("Additional root must be inside repository: " + root);
            Path relative = base.relativize(absolute);
            for (Path segment : relative) {
                if (EXCLUDED.contains(segment.toString())) throw new IllegalArgumentException("Additional root is excluded: " + root);
            }
            explicit.add(relative);
        }
        var files = new ArrayList<Path>();
        var directories = new ArrayList<Path>();
        var diagnostics = new ArrayList<Diagnostic>();
        Files.walkFileTree(base, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                if (!directory.equals(base) && EXCLUDED.contains(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                directories.add(base.relativize(directory));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) files.add(base.relativize(file));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) {
                report(file, failure);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) {
                if (failure != null) report(directory, failure);
                return FileVisitResult.CONTINUE;
            }

            private void report(Path path, IOException failure) {
                diagnostics.add(new Diagnostic("SCAN_ACCESS_FAILURE", base.relativize(path),
                        failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "Access failed")));
                LOG.log(System.Logger.Level.WARNING, "Cannot scan {0}: {1}", path, failure.toString());
            }
        });
        var validExplicit = new ArrayList<Path>();
        for (Path root : explicit) {
            if (directories.contains(root)) validExplicit.add(root);
            else diagnostics.add(new Diagnostic("INVALID_SOURCE_ROOT", root, "Additional root was not a scanned directory"));
        }
        List<Path> roots = new SourceRootDiscovery().discover(directories, validExplicit);
        files.sort(PATH_ORDER);
        for (Path file : files) {
            if (roots.stream().noneMatch(root -> root.toString().isEmpty() || file.startsWith(root))) {
                diagnostics.add(new Diagnostic("SOURCE_ROOT_UNCERTAIN", file,
                        "File is still analyzed; no layout-based source root. AST parsing may infer a root when package and directory agree (including default-package wrapper sources)"));
            }
        }
        diagnostics.sort(Comparator.comparing(Diagnostic::path, PATH_ORDER).thenComparing(Diagnostic::code)
                .thenComparing(Diagnostic::message));
        boolean partial = diagnostics.stream().anyMatch(d -> !d.code().equals("SOURCE_ROOT_UNCERTAIN"));
        return new RepositorySources(base, files, roots, diagnostics, partial);
    }
}
