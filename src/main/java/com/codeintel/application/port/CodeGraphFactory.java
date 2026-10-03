package com.codeintel.application.port;

import com.codeintel.domain.graph.CodeGraph;
import com.codeintel.domain.model.SourceUnit;
import java.util.List;

/** Constructs one immutable indexed graph from completed source analysis. */
@FunctionalInterface
public interface CodeGraphFactory {
    CodeGraph build(List<SourceUnit> sources);
}
