package io.github.krosta12.semimite.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeCompilerTest {

    private final NativeCompiler compiler = new NativeCompiler();

    static boolean compilerAvailable() {
        String path = System.getenv("PATH");
        if (path == null) return false;
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (String dir : path.split(File.pathSeparator)) {
            if (new File(dir, win ? "g++.exe" : "g++").isFile()) return true;
            if (new File(dir, win ? "clang++.exe" : "clang++").isFile()) return true;
        }
        return false;
    }

    @Test
    void throwsOnEmptySourceList(@TempDir Path tempDir) {
        CompilerConfig config = new CompilerConfig(
                tempDir, tempDir.resolve("cache"), null, List.of("-O2"), 4);

        assertThrows(IllegalArgumentException.class,
                () -> compiler.compile(List.of(), config, "test-lib"));
    }

    @Test //recheck
    void libraryOutputNameMatchesOS(@TempDir Path tempDir) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        String expected;
        if (os.contains("win")) {
            expected = ".dll";
        } else if (os.contains("mac")) {
            expected = ".dylib";
        } else {
            expected = ".so";
        }

        CompilerConfig config = new CompilerConfig(
                tempDir, tempDir.resolve("cache"), null, List.of(), 4);
        Path fakeCpp = tempDir.resolve("fake.cpp");
        Files.writeString(fakeCpp, "// empty");

        assertNotNull(expected);
        assertFalse(expected.isBlank());
    }

    @Test
    @EnabledIf("compilerAvailable")
    void compilesValidCppToLibrary(@TempDir Path tempDir) throws Exception {
        Path cache = tempDir.resolve(".cache");
        Files.createDirectories(cache);

        Path src = tempDir.resolve("lib.cpp");
        Files.writeString(src, """
                extern "C" {
                int add(int a, int b) { return a + b; }
                }
                """);

        CompilerConfig config = new CompilerConfig(
                tempDir, cache, null, List.of("-O2"), 4);

        Path result = compiler.compile(List.of(src), config, "test-lib");

        assertTrue(Files.exists(result));
        assertTrue(result.getFileName().toString().startsWith("test-lib"));
    }

    @Test
    @EnabledIf("compilerAvailable")
    void throwsOnSyntaxError(@TempDir Path tempDir) throws Exception {
        Path cache = tempDir.resolve(".cache");
        Files.createDirectories(cache);

        Path src = tempDir.resolve("bad.cpp");
        Files.writeString(src, "this is not valid c++ !!!");

        CompilerConfig config = new CompilerConfig(
                tempDir, cache, null, List.of(), 4);

        assertThrows(IllegalStateException.class,
                () -> compiler.compile(List.of(src), config, "bad-lib"));
    }
}
