package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the semi-mite framework, bound from the
 * {@code mite.*} namespace in {@code application.properties} or {@code application.yml}.
 *
 * <p>All properties are optional. Sensible defaults are provided for every field,
 * so no configuration is required for a basic setup with {@code .cpp} files placed
 * in a {@code cppScripts} directory at the project root.
 *
 * <p>Example {@code application.properties}:
 * <pre>{@code
 * mite.scripts-dir=cppScripts
 * mite.cache-dir=.mite-cache
 * mite.compiler-flags=-Ofast,-march=native,-flto=auto,-fno-plt,-fomit-frame-pointer,-DNDEBUG
 * mite.alignment-bytes=32
 * }</pre>
 *
 * @see MiteAutoConfiguration
 * @see MiteContext
 */
@ConfigurationProperties(prefix = "mite")
public class MiteProperties {

    /**
     * Directory scanned for {@code .cpp} source files containing {@code // @mite}-marked functions.
     *
     * <p>Resolved relative to the application working directory.
     * Default: {@code cppScripts}
     */
    private Path scriptsDir = Path.of("cppScripts");

    /**
     * Directory where compiled shared libraries ({@code .dll} / {@code .so}) are stored
     * and reused across restarts. Compiled artifacts are cached by source file path
     * and last-modified timestamp, so recompilation only occurs when source files change.
     *
     * <p>Default: {@code .mite-cache}
     */
    private Path cacheDir = Path.of(".mite-cache");

    /**
     * Explicit path to the C++ compiler binary.
     *
     * <p>When {@code null} or blank, the compiler is resolved automatically from
     * the system {@code PATH} ({@code g++} preferred, then {@code clang++}).
     * On Windows, common MSYS2 and Scoop installation paths are also checked.
     *
     * <p>Set this property when multiple compiler versions are installed or when
     * the compiler is not on the system {@code PATH}:
     * <pre>{@code
     * mite.compiler-path=C:/mingw64/bin/g++.exe
     * }</pre>
     *
     * <p>Default: {@code null} (auto-detect)
     */
    private String compilerPath = null;

    /**
     * Compiler flags passed directly to {@code g++} or {@code clang++}, comma-separated.
     *
     * <p>Flags are split on commas and whitespace before being passed to the compiler,
     * so both {@code -O3,-march=native} and {@code -O3 -march=native} are accepted.
     *
     * <p>Performance impact varies significantly by workload - see the benchmark
     * section in the README for measured results across different flag configurations.
     *
     * <p>Default: {@code -O2}
     */
    private List<String> compilerFlags = new ArrayList<>(List.of("-O2"));

    /**
     * Memory alignment boundary in bytes used when laying out native struct fields
     * during object marshalling.
     *
     * <p>The default value of {@code 4} is correct for most workloads. Set to {@code 32}
     * when your C++ code uses AVX2 or AVX-512 intrinsics that require 256-bit aligned memory.
     *
     * <p><b>Warning:</b> mismatched alignment between the Java marshalling layer and
     * the C++ struct definition will produce incorrect data without any runtime error.
     * If unsure, leave this at the default.
     *
     * <p>Default: {@code 4}
     */
    private int alignmentBytes = 4;

    /**
     * Returns the explicit compiler binary path, or {@code null} if auto-detection is used.
     *
     * @return compiler path string, or {@code null}
     */
    public String getCompilerPath() {
        return compilerPath;
    }

    /**
     * Sets an explicit path to the C++ compiler binary.
     *
     * @param compilerPath absolute or relative path to the compiler executable;
     *                     pass {@code null} to use auto-detection
     */
    public void setCompilerPath(String compilerPath) {
        this.compilerPath = compilerPath;
    }

    /**
     * Returns the cache directory where compiled libraries are stored.
     *
     * @return path to the cache directory
     */
    public Path getCacheDir() {
        return cacheDir;
    }

    /**
     * Sets the cache directory where compiled libraries are stored.
     *
     * @param cacheDir path to the desired cache directory;
     *                 created automatically if it does not exist
     */
    public void setCacheDir(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Returns the directory scanned for {@code .cpp} source files.
     *
     * @return path to the scripts directory
     */
    public Path getScriptsDir() {
        return scriptsDir;
    }

    /**
     * Sets the directory scanned for {@code .cpp} source files.
     *
     * @param scriptsDir path to the directory containing {@code // @mite}-marked sources
     */
    public void setScriptsDir(Path scriptsDir) {
        this.scriptsDir = scriptsDir;
    }

    /**
     * Returns the list of compiler flags passed to the C++ compiler.
     *
     * @return mutable list of compiler flag strings
     */
    public List<String> getCompilerFlags() {
        return compilerFlags;
    }

    /**
     * Sets the compiler flags passed to the C++ compiler.
     *
     * @param compilerFlags comma- or space-separated flag strings;
     *                      must not be {@code null}
     */
    public void setCompilerFlags(List<String> compilerFlags) {
        this.compilerFlags = compilerFlags;
    }

    /**
     * Returns the memory alignment boundary in bytes.
     *
     * @return alignment in bytes; {@code 4} by default
     */
    public int getAlignmentBytes() {
        return alignmentBytes;
    }

    /**
     * Sets the memory alignment boundary used during native struct layout calculations.
     *
     * @param alignmentBytes alignment in bytes; use {@code 4} for general-purpose workloads,
     *                       {@code 32} for AVX2/AVX-512 SIMD code
     */
    public void setAlignmentBytes(int alignmentBytes) {
        this.alignmentBytes = alignmentBytes;
    }
}