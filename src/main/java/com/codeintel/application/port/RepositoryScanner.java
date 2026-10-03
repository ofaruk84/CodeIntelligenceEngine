package com.codeintel.application.port;

import com.codeintel.application.result.RepositorySources;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Discovers sources without parsing or executing repository content. */
public interface RepositoryScanner {
    RepositorySources scan(Path repository, List<Path> additionalRoots) throws IOException;

    default RepositorySources scan(Path repository) throws IOException {
        return scan(repository, List.of());
    }
}
