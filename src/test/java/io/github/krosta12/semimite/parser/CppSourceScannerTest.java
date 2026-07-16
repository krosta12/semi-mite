package io.github.krosta12.semimite.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CppSourceScannerTest {

    private final CppSourceScanner scanner = new CppSourceScanner();

    @Test
    void returnsEmptyForNonExistentDirectory() {
        List<Path> result = scanner.scan(Path.of("does/not/exist"));

        assertTrue(result.isEmpty());
    }

    @Test
    void findsTopLevelCppFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.cpp"), "//a");
        Files.writeString(dir.resolve("b.cpp"), "//b");

        List<Path> result = scanner.scan(dir);

        assertEquals(2, result.size());
    }

    @Test
    void findsNestedCppFiles(@TempDir Path dir) throws Exception {
        Path sub = dir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("nested.cpp"), "//nested");

        List<Path> result = scanner.scan(dir);

        assertEquals(1, result.size());
        assertTrue(result.get(0).endsWith("nested.cpp"));
    }

    @Test
    void ignoresNonCppFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("header.h"), "//h");
        Files.writeString(dir.resolve("README.md"), "# readme");
        Files.writeString(dir.resolve("code.cpp"), "//cpp");

        List<Path> result = scanner.scan(dir);

        assertEquals(1, result.size());
        assertTrue(result.get(0).toString().endsWith("code.cpp"));
    }

    @Test
    void returnsEmptyForEmptyDirectory(@TempDir Path dir) {
        List<Path> result = scanner.scan(dir);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenPathIsAFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("notdir.cpp");
        Files.writeString(file, "//x");

        List<Path> result = scanner.scan(file);

        assertTrue(result.isEmpty());
    }
}
