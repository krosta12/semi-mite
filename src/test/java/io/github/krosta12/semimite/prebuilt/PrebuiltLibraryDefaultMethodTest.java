package io.github.krosta12.semimite.prebuilt;

import io.github.krosta12.semimite.client.MiteClient;
import io.github.krosta12.semimite.compiler.CompilerConfig;
import io.github.krosta12.semimite.compiler.NativeCompiler;
import io.github.krosta12.semimite.core.Mite;
import io.github.krosta12.semimite.core.MiteContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code Mite.use(...)} must ignore default and static interface methods. */
class PrebuiltLibraryDefaultMethodTest {

    @MiteClient
    public interface Calculator {
        int add(int a, int b);

        default int twice(int a) {
            return add(a, a);
        }

        static int unrelated() {
            return 0;
        }
    }

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
    @EnabledIf("compilerAvailable")
    void defaultAndStaticMethodsAreNotLinked(@TempDir(cleanup = CleanupMode.ON_SUCCESS) Path tempDir) throws Exception {
        Path scriptsDir = tempDir.resolve("cppScripts");
        Files.createDirectories(scriptsDir);
        Path cpp = scriptsDir.resolve("calc.cpp");
        Files.writeString(cpp, """
                extern "C" {

                // @mite
                int add(int a, int b) {
                    return a + b;
                }

                }
                """);

        CompilerConfig config = new CompilerConfig(
                scriptsDir,
                tempDir.resolve(".mite-cache"),
                null,
                List.of("-O2"),
                (int) java.lang.foreign.ValueLayout.ADDRESS.byteSize());

        Path library = new NativeCompiler().compile(List.of(cpp), config, "prebuilt-calc");

        try (MiteContext context = Mite.use("io.github.krosta12.semimite.prebuilt", library).start()) {
            Calculator calc = context.get(Calculator.class);

            assertEquals(5, calc.add(2, 3));
            assertEquals(4, calc.twice(2), "default methods must be invoked without a native symbol");
        }
    }
}
