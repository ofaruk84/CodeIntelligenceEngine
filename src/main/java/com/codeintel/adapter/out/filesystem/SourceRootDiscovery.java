package com.codeintel.adapter.out.filesystem;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/** Layout-only discovery. Package-based inference belongs to the later AST parser. */
public final class SourceRootDiscovery {
    public List<Path> discover(Collection<Path> directories, Collection<Path> additionalRoots) {
        return Stream.concat(directories.stream().filter(this::isStandardRoot), additionalRoots.stream())
                .distinct().sorted(FileSystemRepositoryScanner.PATH_ORDER).toList();
    }

    private boolean isStandardRoot(Path path) {
        int size = path.getNameCount();
        return size >= 3 && path.getName(size - 3).toString().equals("src")
                && (path.getName(size - 2).toString().equals("main")
                    || path.getName(size - 2).toString().equals("test"))
                && path.getName(size - 1).toString().equals("java");
    }
}
