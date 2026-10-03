package com.codeintel.application.service;

import com.codeintel.application.port.*;
import com.codeintel.application.result.AnalysisSnapshot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Scan, analyze and construct indexes exactly once per requested snapshot. */
public final class RepositoryAnalysisService {
    private static final System.Logger LOG = System.getLogger(RepositoryAnalysisService.class.getName());
    private final RepositoryScanner scanner;
    private final SourceAnalyzer analyzer;
    private final CodeGraphFactory graphs;
    public RepositoryAnalysisService(RepositoryScanner scanner, SourceAnalyzer analyzer, CodeGraphFactory graphs) {
        this.scanner = Objects.requireNonNull(scanner);
        this.analyzer = Objects.requireNonNull(analyzer);
        this.graphs = Objects.requireNonNull(graphs);
    }
    public AnalysisSnapshot analyze(Path repository) throws IOException {
        return analyze(repository, AnalysisOptions.defaults());
    }
    public AnalysisSnapshot analyze(Path repository, AnalysisOptions options) throws IOException {
        var sources = scanner.scan(repository, Objects.requireNonNull(options).additionalRoots());
        var units = java.util.List.copyOf(analyzer.analyze(sources, options.dependencyJars()));
        var snapshot = new AnalysisSnapshot(sources, units, graphs.build(units));
        LOG.log(System.Logger.Level.INFO, "Analyzed {0} source files", units.size());
        return snapshot;
    }
}
