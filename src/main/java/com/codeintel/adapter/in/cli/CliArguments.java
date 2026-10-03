package com.codeintel.adapter.in.cli;

import com.codeintel.application.service.AnalysisOptions;
import java.nio.file.Path;
import java.util.*;

/** Shell token parsing only; no source inspection. */
public record CliArguments(String command, Path repository, List<String> operands, AnalysisOptions options, boolean detailedDiagnostics, Path diagnosticsFile) {
    public CliArguments { operands = List.copyOf(operands); }
    private static final Map<String, Integer> ARITY = Map.of("scan", 0, "search", 1,
            "method", 1, "callers", 1, "callees", 1, "dependencies", 1, "path", 2, "impact", 1);
    public static CliArguments parse(String[] args) {
        if (args.length == 0 || (args.length == 1 && Set.of("help", "--help", "-h").contains(args[0])))
            return new CliArguments("help", null, List.of(), AnalysisOptions.defaults(), false, null);
        var tokens = new ArrayList<String>();
        var roots = new ArrayList<Path>();
        var jars = new ArrayList<Path>();
        boolean detailed = false;
        Path diagnosticsFile = null;
        boolean literal = false;
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (!literal && token.equals("--")) { literal = true; continue; }
            if (!literal && token.equals("--source-root")) {
                if (++i == args.length || args[i].isBlank() || args[i].startsWith("--"))
                    throw new IllegalArgumentException("--source-root requires a path");
                roots.add(Path.of(args[i]));
            } else if (!literal && token.equals("--detailed-diagnostics")) {
                detailed = true;
            } else if (!literal && (token.equals("--dependency-jar") || token.equals("--diagnostics-file"))) {
                if (++i == args.length || args[i].isBlank() || args[i].startsWith("--"))
                    throw new IllegalArgumentException(token + " requires a path");
                if (token.equals("--dependency-jar")) jars.add(Path.of(args[i]));
                else {
                    if (diagnosticsFile != null) throw new IllegalArgumentException("--diagnostics-file may occur only once");
                    diagnosticsFile = Path.of(args[i]);
                }
            } else if (!literal && token.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option: " + token);
            } else tokens.add(token);
        }
        if (tokens.isEmpty() || !ARITY.containsKey(tokens.getFirst()))
            throw new IllegalArgumentException("Unknown or missing command");
        String command = tokens.getFirst();
        if (tokens.size() != ARITY.get(command) + 2)
            throw new IllegalArgumentException("Wrong argument count for " + command);
        if (tokens.get(1).isBlank()) throw new IllegalArgumentException("Repository path must not be blank");
        if (!command.equals("search") && tokens.subList(2, tokens.size()).stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Method IDs must not be blank");
        return new CliArguments(command, Path.of(tokens.get(1)), tokens.subList(2, tokens.size()),
                new AnalysisOptions(roots, jars), detailed, diagnosticsFile);
    }
}
