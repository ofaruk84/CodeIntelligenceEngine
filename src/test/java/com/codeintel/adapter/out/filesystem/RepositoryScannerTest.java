package com.codeintel.adapter.out.filesystem;

import com.codeintel.application.result.RepositorySources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RepositoryScannerTest {
    @TempDir Path repository;
    private final FileSystemRepositoryScanner scanner = new FileSystemRepositoryScanner();

    private Path source(String relative) throws IOException {
        Path file = repository.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "// Synthetic scanner fixture; no parsing required");
    }

    private List<String> strings(List<Path> paths) {
        return paths.stream().map(p -> p.toString().replace('\\', '/')).toList();
    }

    @Test void recursivelyDiscoversAndSortsNormalizedPaths() throws IOException {
        source("z/deep/Z.java");
        source("a/B.java");
        source("a/A.java");
        source("a/ignored.txt");
        source("a/Upper.JAVA");
        RepositorySources result = scanner.scan(repository);
        assertEquals(List.of("a/A.java", "a/B.java", "z/deep/Z.java"), strings(result.files()));
        assertEquals(result, scanner.scan(repository.resolve(".")));
        assertFalse(result.partial());
        assertEquals(3, result.diagnostics().size());
    }

    @Test void excludesDirectorySegmentsAtEveryDepth() throws IOException {
        for (String ignored : List.of("target", "build", ".gradle", ".idea", ".git", "node_modules")) {
            source(ignored + "/Root.java");
            source("module/" + ignored + "/src/main/java/Hidden.java");
        }
        source("targeted/Visible.java");
        assertEquals(List.of("targeted/Visible.java"), strings(scanner.scan(repository).files()));
    }

    @Test void rejectsMissingFilesAndSymlinkRepositoryPaths() throws IOException {
        assertThrows(IOException.class, () -> scanner.scan(repository.resolve("missing")));
        Path file = source("File.java");
        assertTrue(assertThrows(IOException.class, () -> scanner.scan(file)).getMessage().contains("directory"));
    }

    @Test void discoversStandardRootsThroughoutSubmodulesIncludingEmptyRoots() throws IOException {
        source("orders/src/main/java/example/Order.java");
        source("payments/src/test/java/example/PaymentTest.java");
        Files.createDirectories(repository.resolve("empty/src/main/java"));
        var result = scanner.scan(repository);
        assertEquals(List.of("empty/src/main/java", "orders/src/main/java", "payments/src/test/java"), strings(result.sourceRoots()));
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test void explicitRootsAreNormalizedDeduplicatedAndDoNotRestrictDiscovery() throws IOException {
        source("custom/example/Custom.java");
        source("other/Other.java");
        var result = scanner.scan(repository, List.of(Path.of("custom/../custom"), repository.resolve("custom")));
        assertEquals(List.of("custom"), strings(result.sourceRoots()));
        assertEquals(List.of("custom/example/Custom.java", "other/Other.java"), strings(result.files()));
        assertEquals("SOURCE_ROOT_UNCERTAIN", result.diagnostics().getFirst().code());
        assertEquals(Path.of("other/Other.java"), result.diagnostics().getFirst().path());
    }

    @Test void explicitRepositoryRootCoversNonstandardLayout() throws IOException {
        source("loose/File.java");
        assertTrue(scanner.scan(repository, List.of(Path.of("."))).diagnostics().isEmpty());
    }

    @Test void reportsInvalidExplicitRootsWithoutDroppingSources() throws IOException {
        source("Good.java");
        var result = scanner.scan(repository, List.of(Path.of("missing"), Path.of("Good.java")));
        assertEquals(List.of(Path.of("Good.java")), result.files());
        assertTrue(result.partial());
        assertEquals(2, result.diagnostics().stream().filter(d -> d.code().equals("INVALID_SOURCE_ROOT")).count());
        assertThrows(IllegalArgumentException.class, () -> scanner.scan(repository, List.of(Path.of("../outside"))));
        assertThrows(IllegalArgumentException.class, () -> scanner.scan(repository, List.of(Path.of("target"))));
    }

    @Test void resultDefensivelyCopiesCollections() {
        var files = new java.util.ArrayList<Path>();
        files.add(Path.of("A.java"));
        var result = new RepositorySources(repository, files, List.of(), List.of(), false);
        files.clear();
        assertEquals(1, result.files().size());
        assertThrows(UnsupportedOperationException.class, () -> result.files().clear());
    }

    @Test void doesNotFollowDirectoryOrFileSymlinks() throws IOException {
        Path real = source("real/src/main/java/Real.java");
        try {
            Files.createSymbolicLink(repository.resolve("linked"), real.getParent());
            Files.createSymbolicLink(repository.resolve("Alias.java"), real);
        } catch (UnsupportedOperationException | IOException | SecurityException failure) {
            assumeTrue(false, "Symbolic links unavailable: " + failure);
        }
        var result = scanner.scan(repository, List.of(Path.of("linked")));
        assertEquals(List.of("real/src/main/java/Real.java"), strings(result.files()));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("INVALID_SOURCE_ROOT")));
        assertThrows(IOException.class, () -> scanner.scan(repository.resolve("linked")));
    }

    @Test void retainsSuccessfulFilesWhenDirectoryAccessFails() throws IOException {
        assumeTrue(Files.getFileStore(repository).supportsFileAttributeView("posix"), "POSIX permissions unavailable");
        source("good/Good.java");
        source("blocked/Hidden.java");
        Path blocked = repository.resolve("blocked");
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(blocked);
        try {
            Files.setPosixFilePermissions(blocked, Set.of());
            assumeTrue(!Files.isReadable(blocked), "Current user can bypass directory permissions");
            var result = scanner.scan(repository);
            assertEquals(List.of("good/Good.java"), strings(result.files()));
            assertTrue(result.partial());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("SCAN_ACCESS_FAILURE")
                    && d.path().equals(Path.of("blocked"))));
        } finally {
            Files.setPosixFilePermissions(blocked, permissions);
        }
    }
    @Test void mixedLayoutsCombineStandardAndExplicitRootsWithoutDroppingLooseSources() throws IOException {
        source("module/src/main/java/example/Main.java");
        source("module/src/test/java/example/MainTest.java");
        source("generated/example/Generated.java");
        source("legacy/deep/Loose.java");
        var discovered = scanner.scan(repository);
        assertFalse(discovered.partial());
        assertEquals(List.of("generated/example/Generated.java", "legacy/deep/Loose.java"), strings(discovered.diagnostics().stream().map(RepositorySources.Diagnostic::path).toList()));
        assertTrue(discovered.diagnostics().stream().allMatch(d -> d.code().equals("SOURCE_ROOT_UNCERTAIN") && !d.message().isBlank()));
        var explicit = scanner.scan(repository, List.of(Path.of("generated")));
        assertEquals(discovered.files(), explicit.files());
        assertEquals(List.of("generated", "module/src/main/java", "module/src/test/java"), strings(explicit.sourceRoots()));
        assertEquals(List.of(Path.of("legacy/deep/Loose.java")), explicit.diagnostics().stream().map(RepositorySources.Diagnostic::path).toList());
        assertFalse(explicit.partial());
    }
    @Test void emptyRepositoryIsACompleteEmptyScan() throws IOException {
        var result = scanner.scan(repository);
        assertTrue(result.files().isEmpty());
        assertTrue(result.sourceRoots().isEmpty());
        assertTrue(result.diagnostics().isEmpty());
        assertFalse(result.partial());
    }
}
