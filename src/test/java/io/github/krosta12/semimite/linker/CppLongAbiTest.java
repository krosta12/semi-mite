package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.compiler.CompilerConfig;
import io.github.krosta12.semimite.compiler.NativeCompiler;
import io.github.krosta12.semimite.compiler.OsUtil;
import io.github.krosta12.semimite.linker.NativeLibraryLinker.LinkedLibrary;
import io.github.krosta12.semimite.parser.NativeFunctionSignature;
import io.github.krosta12.semimite.parser.NativeFunctionSignature.NativeParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D1 characterization test: C++ {@code long} is 4 bytes on Windows (LLP64)
 * and 8 bytes on Linux/macOS (LP64), while Java {@code long} is always 8.
 *
 * <p>This test pins the current behaviour instead of silently assuming that
 * the same Java signature works everywhere:</p>
 *
 * <ul>
 *   <li>on Windows {@code long f(long)} fails with {@link ClassCastException}
 *       because the descriptor is {@code JAVA_INT};</li>
 *   <li>on LP64 the same call succeeds;</li>
 *   <li>{@code long long} works on both platforms.</li>
 * </ul>
 *
 * <p>If D1 is fixed, the Windows branch will start failing: that is the signal
 * to remove it and update chapter 16 / this test.</p>
 */
class CppLongAbiTest {

    @FunctionalInterface
    interface LongCall {
        long call();
    }

    public interface LongClient {
        long ladd(long a, long b);

        long lret();

        long lladd(long a, long b);
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

    private Path compileLongLibrary(Path tempDir) throws Exception {
        Path scripts = tempDir.resolve("cppScripts");
        Files.createDirectories(scripts);
        Path cpp = scripts.resolve("long_abi.cpp");
        Files.writeString(cpp, """
                extern "C" {

                // @mite
                long ladd(long a, long b) {
                    return a + b;
                }

                // @mite
                long lret() {
                    return 7;
                }

                // @mite
                long long lladd(long long a, long long b) {
                    return a + b;
                }

                }
                """);

        CompilerConfig config = new CompilerConfig(
                scripts,
                tempDir.resolve(".mite-cache"),
                null,
                List.of("-O2"),
                (int) java.lang.foreign.ValueLayout.ADDRESS.byteSize());

        return new NativeCompiler().compile(List.of(cpp), config, "long-abi");
    }

    @Test
    @EnabledIf("compilerAvailable")
    void cppLongWithJavaLongIsPlatformDependent(@TempDir Path tempDir) throws Exception {
        Path library = compileLongLibrary(tempDir);

        List<NativeFunctionSignature> signatures = List.of(
                new NativeFunctionSignature("ladd", "long",
                        List.of(new NativeParam("long", "a"), new NativeParam("long", "b")), "long_abi.cpp"),
                new NativeFunctionSignature("lret", "long",
                        List.of(), "long_abi.cpp"),
                new NativeFunctionSignature("lladd", "long long",
                        List.of(new NativeParam("long long", "a"), new NativeParam("long long", "b")),
                        "long_abi.cpp")
        );

        try (LinkedLibrary linked = new NativeLibraryLinker().link(library, signatures)) {
            ManagedLibrary managed = new ManagedLibrary(linked);
            LongClient client = new MiteClientProxyFactory().create(LongClient.class, managed);

            // long long is portable: Java long == 8 bytes everywhere.
            assertEquals(5L, client.lladd(2L, 3L), "long long must work on every platform");

            if (OsUtil.isWindows()) {
                // D1: C++ long is 4 bytes, descriptor is JAVA_INT, Java sends Long.
                IllegalStateException callFailure = assertThrows(
                        IllegalStateException.class,
                        () -> client.ladd(2L, 3L),
                        "Windows C++ long + Java long must currently fail (D1)");
                assertInstanceOf(ClassCastException.class, callFailure.getCause(),
                        "failure must be the Long-to-Integer ABI mismatch, not a marshalling error");

                // Return path: native returns Integer, Java method expects long.
                assertThrows(ClassCastException.class, client::lret,
                        "Windows C++ long return + Java long must currently fail (D1)");
            } else {
                // LP64: C++ long is 8 bytes, so Java long matches.
                assertEquals(5L, client.ladd(2L, 3L),
                        "on LP64 C++ long and Java long are both 8 bytes");
                assertEquals(7L, client.lret(),
                        "on LP64 C++ long return matches Java long");
            }
        }
    }
}
