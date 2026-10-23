package io.github.krosta12.semimite.compiler;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration used to compile and cache the native (C++) library.
 *
 * @param scriptsDir     directory containing the {@code .cpp} source files
 * @param cacheDir       directory where compiled native libraries are cached
 * @param compilerPath   explicit path to the compiler executable, or {@code null}
 *                       to auto-resolve via {@link CompilerResolver}
 * @param compilerFlags  additional flags passed to the compiler invocation
 * @param pointerSize    native pointer size in bytes (4 or 8); it must match the
 *                       address size of the JVM, so on a 64-bit JVM only 8 is accepted
 */
public record CompilerConfig(
        Path scriptsDir,
        Path cacheDir,
        String compilerPath,
        List<String> compilerFlags,
        int pointerSize
) {

    /**
     * Returns a default configuration: sources in {@code cppScripts},
     * cache in {@code .mite-cache}, auto-resolved compiler, {@code -O2} flag,
     * and an 8-byte pointer size.
     *
     * @return the default {@code CompilerConfig}
     */
    public static CompilerConfig defaults() {
        return new CompilerConfig(
                Path.of("cppScripts"),
                Path.of(".mite-cache"),
                null,
                List.of("-O2"),
                8
        );
    }

}