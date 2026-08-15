package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.compiler.CompilerConfig;
import io.github.krosta12.semimite.compiler.NativeCompiler;
import io.github.krosta12.semimite.parser.NativeFunctionSignature;
import io.github.krosta12.semimite.parser.NativeFunctionSignature.NativeParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeLibraryLinkerTest {

    private final NativeLibraryLinker linker = new NativeLibraryLinker();
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

    private Path buildLibrary(Path tempDir, String cpp, String libName) throws Exception {
        Path cache = tempDir.resolve(".cache");
        Files.createDirectories(cache);
        Path src = tempDir.resolve(libName + ".cpp");
        Files.writeString(src, cpp);
        CompilerConfig config = new CompilerConfig(tempDir, cache, null, List.of(), 4);
        return compiler.compile(List.of(src), config, libName);
    }

    @Test
    @EnabledIf("compilerAvailable")
    void linksMultipleFunctionsAndInvokesThem(@TempDir Path tempDir) throws Throwable {
        Path lib = buildLibrary(tempDir, """
                extern "C" {
                int add(int a, int b) { return a + b; }
                double square(double x) { return x * x; }
                }
                """, "multi-lib");

        List<NativeFunctionSignature> sigs = List.of(
                new NativeFunctionSignature("add", "int",
                        List.of(new NativeParam("int", "a"), new NativeParam("int", "b")), "multi.cpp"),
                new NativeFunctionSignature("square", "double",
                        List.of(new NativeParam("double", "x")), "multi.cpp")
        );

        try (NativeLibraryLinker.LinkedLibrary linked = linker.link(lib, sigs)) {
            Map<String, MethodHandle> handles = linked.handles();
            assertEquals(2, handles.size());

            assertEquals(7, (int) handles.get("add").invoke(3, 4));
            assertEquals(9.0, (double) handles.get("square").invoke(3.0));
        }
    }

    @Test
    @EnabledIf("compilerAvailable")
    void closingLinkedLibraryInvalidatesHandles(@TempDir Path tempDir) throws Throwable {
        Path lib = buildLibrary(tempDir, """
                extern "C" {
                int add(int a, int b) { return a + b; }
                }
                """, "close-lib");

        List<NativeFunctionSignature> sigs = List.of(
                new NativeFunctionSignature("add", "int",
                        List.of(new NativeParam("int", "a"), new NativeParam("int", "b")), "multi.cpp")
        );

        NativeLibraryLinker.LinkedLibrary linked = linker.link(lib, sigs);
        MethodHandle add = linked.handles().get("add");
        assertEquals(7, (int) add.invoke(3, 4));

        linked.close();

        assertThrows(IllegalStateException.class, () -> add.invoke(1, 2));
    }

    @Test
    void throwsWhenLibraryFileDoesNotExist(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.so");
        List<NativeFunctionSignature> sigs = List.of(
                new NativeFunctionSignature("add", "int",
                        List.of(new NativeParam("int", "a"), new NativeParam("int", "b")), "multi.cpp")
        );

        assertThrows(RuntimeException.class, () -> linker.link(missing, sigs));
    }
}