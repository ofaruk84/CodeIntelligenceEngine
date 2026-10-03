package com.codeintel.application.service;

import java.nio.file.Path;
import java.util.List;

public record AnalysisOptions(List<Path> additionalRoots) {
    public AnalysisOptions { additionalRoots = List.copyOf(additionalRoots); }
    public static AnalysisOptions defaults() { return new AnalysisOptions(List.of()); }
}
