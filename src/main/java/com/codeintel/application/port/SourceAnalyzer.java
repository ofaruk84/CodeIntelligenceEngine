package com.codeintel.application.port;

import com.codeintel.application.result.RepositorySources;
import com.codeintel.domain.model.SourceUnit;
import java.util.List;

/** Source extraction and static call resolution; results retain input file order, including failed files. */
public interface SourceAnalyzer {
    List<SourceUnit> analyze(RepositorySources sources);
}
