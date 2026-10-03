package com.codeintel.adapter.out.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaParserSetupTest {
    @Test
    void parsesJava21RecordPatternsAndResolvesJdkCalls() {
        var solver = new CombinedTypeSolver(new ReflectionTypeSolver(true));
        var parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(solver)));
        var result = parser.parse("""
                record Message(String text) {}
                class Example {
                    int length(Object value) {
                        return switch (value) {
                            case Message(String text) -> text.length();
                            default -> 0;
                        };
                    }
                }
                """);
        assertTrue(result.isSuccessful(), () -> result.getProblems().toString());
        var call = result.getResult().orElseThrow().findFirst(MethodCallExpr.class).orElseThrow();
        assertEquals("java.lang.String.length()", call.resolve().getQualifiedSignature());
    }

    @Test
    void resolvesCallsAcrossLocalSourceFiles(@TempDir Path sourceRoot) throws IOException {
        var packageDir = Files.createDirectories(sourceRoot.resolve("example"));
        Files.writeString(packageDir.resolve("Greeting.java"), """
                package example;
                public class Greeting {
                    public static String hello() { return "Hello"; }
                }
                """);
        var caller = packageDir.resolve("Caller.java");
        Files.writeString(caller, """
                package example;
                class Caller {
                    String call() { return Greeting.hello(); }
                }
                """);
        var solver = new CombinedTypeSolver(
                new JavaParserTypeSolver(sourceRoot), new ReflectionTypeSolver(true));
        var parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(solver)));
        var result = parser.parse(caller);
        assertTrue(result.isSuccessful(), () -> result.getProblems().toString());
        var call = result.getResult().orElseThrow().findFirst(MethodCallExpr.class).orElseThrow();
        assertEquals("example.Greeting.hello()", call.resolve().getQualifiedSignature());
    }
}
