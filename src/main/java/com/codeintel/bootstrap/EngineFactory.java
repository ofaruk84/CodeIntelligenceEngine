package com.codeintel.bootstrap;

import com.codeintel.adapter.out.filesystem.FileSystemRepositoryScanner;
import com.codeintel.adapter.out.javaparser.JavaParserSourceAnalyzer;
import com.codeintel.adapter.out.graph.InMemoryCodeGraph;
import com.codeintel.application.service.RepositoryAnalysisService;

/** Composition root for the local, in-memory engine. */
public final class EngineFactory {
    private EngineFactory() {}
    public static RepositoryAnalysisService create() {
        return new RepositoryAnalysisService(new FileSystemRepositoryScanner(),
                new JavaParserSourceAnalyzer(), InMemoryCodeGraph::fromSources);
    }
}
