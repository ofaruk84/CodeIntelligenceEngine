package com.codeintel.application.service;

import java.nio.file.Path;
import java.util.List;

/** Explicit local analysis inputs; relative dependency paths use the process working directory. */
public record AnalysisOptions(List<Path> additionalRoots, List<Path> dependencyJars) {
    public AnalysisOptions {
        additionalRoots = List.copyOf(additionalRoots);
        dependencyJars = List.copyOf(dependencyJars);
    }
    public AnalysisOptions(List<Path> additionalRoots) { this(additionalRoots, List.of()); }
    public static AnalysisOptions defaults() { return new AnalysisOptions(List.of()); }
}
