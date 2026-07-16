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
 * @param alignmentBytes native pointer/alignment size in bytes (4 or 8)
 */
public record CompilerConfig(
        Path scriptsDir,
        Path cacheDir,
        String compilerPath,
        List<String> compilerFlags,
        int alignmentBytes
) {

    /**
     * Returns a default configuration: sources in {@code cppScripts},
     * cache in {@code .mite-cache}, auto-resolved compiler, {@code -O2} flag,
     * and 8-byte alignment.
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