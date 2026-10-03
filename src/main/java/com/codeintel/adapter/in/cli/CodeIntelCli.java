package com.codeintel.adapter.in.cli;

import com.codeintel.application.service.*;
import java.io.*;
import java.nio.file.InvalidPathException;
import java.util.Objects;

/** Incoming adapter: analyze once, query that snapshot, present application outcomes. */
public final class CodeIntelCli {
    private final RepositoryAnalysisService analysis;
    public CodeIntelCli(RepositoryAnalysisService analysis) { this.analysis = Objects.requireNonNull(analysis); }
    public int run(String[] args, PrintStream out, PrintStream err) {
        final CliArguments arguments;
        try { arguments = CliArguments.parse(args); }
        catch (IllegalArgumentException failure) { err.println("Usage error: " + failure.getMessage()); err.println("Use --help for usage."); return 2; }
        if (arguments.command().equals("help")) { CliRenderer.help(out); return 0; }
        try {
            var snapshot = analysis.analyze(arguments.repository(), arguments.options());
            var renderer = new CliRenderer(out, err);
            renderer.summary(snapshot);
            renderer.coverage(snapshot.coverage(), arguments.detailedDiagnostics());
            if (arguments.diagnosticsFile() != null) {
                try { DiagnosticsWriter.write(arguments.diagnosticsFile(), snapshot); }
                catch (IOException | SecurityException failure) {
                    err.println("Cannot save diagnostics: " + failure.getMessage()); return 3;
                }
            }
            boolean found = renderer.query(arguments, new CodeIntelligenceService(snapshot), snapshot);
            if (!found) return 4;
            return snapshot.coverage().partialScan() || !snapshot.coverage().partialSources().isEmpty() ? 5 : 0;
        } catch (UnknownMethodException failure) {
            err.println(failure.getMessage()); return 4;
        } catch (IOException | InvalidPathException failure) {
            err.println("Invalid repository input: " + failure.getClass().getSimpleName() + ": " + failure.getMessage()); return 3;
        } catch (IllegalArgumentException failure) {
            err.println("Invalid analysis input: " + failure.getMessage()); return 3;
        } catch (RuntimeException failure) {
            err.println("Fatal analysis failure: " + failure.getClass().getSimpleName() + ": " + failure.getMessage()); return 1;
        }
    }
}
