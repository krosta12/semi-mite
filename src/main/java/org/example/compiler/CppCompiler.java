package org.example.compiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compiles C++ source files into native shared libraries ({@code .dll} on Windows,
 * {@code .so} on Linux/macOS) and caches the results to avoid redundant recompilation.
 *
 * <p>Two compilation modes are supported:
 * <ul>
 *   <li>{@link #compile(Path)} - compiles a {@code .cpp} file from disk. Results are
 *       cached by absolute path and last-modified timestamp. Recompilation only occurs
 *       when the source file has changed since the last build.</li>
 *   <li>{@link #compileInline(String)} - compiles a raw C++ code string passed at
 *       runtime. Results are cached by content hash for the lifetime of the process.</li>
 * </ul>
 *
 * <p>Before compilation, source files are passed through a lightweight preprocessor
 * ({@link #preprocessAndCompile(Path)}) that injects {@code extern "C"} linkage
 * on functions marked with {@code // @mite}, and prepends standard headers required
 * for string handling.
 *
 * <p>The compiler binary is resolved in the following order:
 * <ol>
 *   <li>Explicit path from {@code mite.compiler-path} (if set)</li>
 *   <li>{@code g++} on system {@code PATH}</li>
 *   <li>{@code clang++} on system {@code PATH}</li>
 *   <li>Common MSYS2 and Scoop installation paths (Windows only)</li>
 * </ol>
 *
 * <p>If no compiler is found, a {@link MiteException} is thrown with a message
 * describing how to resolve the issue.
 *
 * @see MiteException
 * @see org.example.scanner.FunctionRegistry
 */
public class CppCompiler {

    private static final Logger log = LoggerFactory.getLogger(CppCompiler.class);

    private final Path cacheDir;
    private final Map<String, CachedLib> cache = new ConcurrentHashMap<>();
    private final String customCompilerPath;
    private final List<String> compilerFlags;

    /**
     * Constructs a new {@code CppCompiler}.
     *
     * <p>The cache directory is created if it does not already exist.
     * Compiler flags are parsed and normalized: blank entries are ignored,
     * and space-separated tokens within a single flag string are split into
     * individual arguments. If the resulting flag list is empty, {@code -O2}
     * is used as the default.
     *
     * @param cacheDir           directory where compiled libraries are stored
     * @param customCompilerPath explicit path to the compiler binary, or {@code null}
     *                           to auto-detect from {@code PATH}
     * @param compilerFlags      list of flags to pass to the compiler; may be
     *                           {@code null} or empty (defaults to {@code -O2})
     * @throws MiteException if the cache directory cannot be created
     */
    public CppCompiler(Path cacheDir, String customCompilerPath, List<String> compilerFlags) {
        this.cacheDir = cacheDir;
        this.customCompilerPath = customCompilerPath;

        List<String> processedFlags = new ArrayList<>();
        if (compilerFlags != null) {
            for (String flag : compilerFlags) {
                if (flag != null && !flag.isBlank()) {
                    String[] split = flag.trim().split("\\s+");
                    processedFlags.addAll(Arrays.asList(split));
                }
            }
        }
        this.compilerFlags = !processedFlags.isEmpty() ? processedFlags : List.of("-O2");

        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            throw new MiteException("Error during cache creation: " + cacheDir, e);
        }
    }

    /**
     * Compiles the given {@code .cpp} file into a shared library.
     *
     * <p>Results are cached by absolute file path and last-modified timestamp.
     * If the file has not changed since the last compilation, the cached library
     * path is returned immediately without invoking the compiler.
     *
     * <p>Before compilation, the source is preprocessed to inject
     * {@code extern "C"} linkage on {@code // @mite}-marked functions
     * and to prepend standard headers.
     *
     * @param cppFile path to the {@code .cpp} source file to compile
     * @return path to the compiled shared library ({@code .dll} or {@code .so})
     * @throws MiteException if preprocessing or compilation fails
     */
    public Path compile(Path cppFile) {
        String key = cppFile.toAbsolutePath().toString();
        long lastModified = lastModified(cppFile);

        CachedLib cached = cache.get(key);
        if (cached != null && cached.timestamp == lastModified) {
            return cached.lib;
        }

        Path preprocessed = preprocessAndCompile(cppFile);
        Path lib = doCompile(preprocessed);
        cache.put(key, new CachedLib(lib, lastModified));
        return lib;
    }

    /**
     * Compiles a raw C++ code string into a shared library.
     *
     * <p>The code is written to a temporary file in the cache directory
     * and compiled from there. Results are cached by the hash of the
     * source string for the lifetime of the process - inline code is
     * never recompiled once cached.
     *
     * @param code the C++ source code to compile
     * @return path to the compiled shared library ({@code .dll} or {@code .so})
     * @throws MiteException if the temporary file cannot be written or compilation fails
     */
    public Path compileInline(String code) {
        String key = "inline:" + code.hashCode();
        CachedLib cached = cache.get(key);
        if (cached != null) return cached.lib;

        try {
            Path tmp = Files.createTempFile(cacheDir, "mite_inline_", ".cpp");
            Files.writeString(tmp, code);
            Path lib = doCompile(tmp);
            cache.put(key, new CachedLib(lib, -1));
            return lib;
        } catch (IOException e) {
            throw new MiteException("Inline code writing error", e);
        }
    }

    /**
     * Invokes the C++ compiler to produce a shared library from the given source file.
     *
     * <p>The output library is placed in the cache directory with a name derived from
     * the source filename and the current nanosecond timestamp to avoid collisions.
     * On Windows, {@code -Wl,--kill-at} is added to strip the decorated symbol names
     * produced by MinGW, ensuring that Panama symbol lookup works correctly.
     *
     * @param cppFile the preprocessed {@code .cpp} file to compile
     * @return path to the produced shared library
     * @throws MiteException if the compiler process fails, exits with a non-zero
     *                       code, or cannot be started
     */
    private Path doCompile(Path cppFile) {
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        String name = cppFile.getFileName().toString().replace(".cpp", "") + "_" + System.nanoTime();
        String libName = win ? name + ".dll" : "lib" + name + ".so";
        Path out = cacheDir.resolve(libName);

        String compiler = findCompiler();

        List<String> cmd = new ArrayList<>();
        cmd.add(compiler);
        cmd.add("-shared");
        if (!win) cmd.add("-fPIC");

        cmd.addAll(compilerFlags);

        if (win) cmd.add("-Wl,--kill-at");
        cmd.add("-o");
        cmd.add(out.toAbsolutePath().toString());
        cmd.add(cppFile.toAbsolutePath().toString());

        log.debug("[MITE COMPILER] CMD flags for C++ compiler: {}", cmd);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (win) {
                pb.environment().put("PATH",
                        "C:\\msys64\\ucrt64\\bin;" + pb.environment().getOrDefault("PATH", ""));
            }
            pb.redirectErrorStream(false);
            Process p = pb.start();

            String stdout = new String(p.getInputStream().readAllBytes());
            String stderr = new String(p.getErrorStream().readAllBytes());
            int exitCode = p.waitFor();

            if (exitCode != 0) throw new MiteException("Compilation error:\n" + stdout + "\n" + stderr);
            return out;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MiteException("Failed to start compiler: " + compiler, e);
        }
    }

    /**
     * Resolves the compiler binary to use for compilation.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>The explicit path from {@code mite.compiler-path} if set and non-blank.</li>
     *   <li>{@code g++} resolved from the system {@code PATH}.</li>
     *   <li>{@code clang++} resolved from the system {@code PATH}.</li>
     *   <li>Common MSYS2 and Scoop installation paths (Windows only).</li>
     * </ol>
     *
     * @return the resolved compiler command or absolute path
     * @throws MiteException if no working compiler is found
     */
    private String findCompiler() {
        if (customCompilerPath != null && !customCompilerPath.isBlank()) {
            return customCompilerPath;
        }

        List<String> candidates = new ArrayList<>(List.of("g++", "clang++"));
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            candidates.addAll(findWindowsCompilers());
        }

        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version")
                        .redirectErrorStream(true)
                        .start();
                p.waitFor();
                if (p.exitValue() == 0) return c;
            } catch (Exception ignored) {
            }
        }

        throw new MiteException(
                "C++ compiler not found. Install g++ or clang++ and add to PATH, " +
                        "or specify explicitly: mite.compiler-path=C:\\path\\to\\g++.exe"
        );
    }

    /**
     * Searches common Windows installation paths for a C++ compiler binary.
     *
     * <p>Checks the following locations:
     * <ul>
     *   <li>MSYS2 roots: {@code C:\msys64}, {@code C:\msys2}, {@code %MSYS2_ROOT%},
     *       and {@code ~\msys64} with {@code ucrt64}, {@code mingw64}, {@code mingw32},
     *       and {@code clang64} subdirectories.</li>
     *   <li>Scoop-installed GCC and LLVM under the current user's home directory.</li>
     * </ul>
     *
     * @return list of absolute paths to compiler binaries found on this system;
     * may be empty if none are found
     */
    private List<String> findWindowsCompilers() {
        List<String> found = new ArrayList<>();

        List<String> msys2Roots = List.of(
                "C:\\msys64",
                "C:\\msys2",
                System.getenv().getOrDefault("MSYS2_ROOT", ""),
                System.getProperty("user.home") + "\\msys64"
        );

        List<String> msys2SubDirs = List.of(
                "ucrt64\\bin\\g++.exe",
                "mingw64\\bin\\g++.exe",
                "mingw32\\bin\\g++.exe",
                "clang64\\bin\\clang++.exe"
        );

        for (String root : msys2Roots) {
            if (root.isBlank()) continue;
            for (String sub : msys2SubDirs) {
                Path path = Path.of(root, sub);
                if (Files.exists(path)) found.add(path.toString());
            }
        }

        String userHome = System.getProperty("user.home");
        List<String> scoopPaths = List.of(
                userHome + "\\scoop\\apps\\gcc\\current\\bin\\g++.exe",
                userHome + "\\scoop\\apps\\llvm\\current\\bin\\clang++.exe"
        );
        for (String p : scoopPaths) {
            if (Files.exists(Path.of(p))) found.add(p);
        }

        return found;
    }

    /**
     * Preprocesses a {@code .cpp} source file before compilation.
     *
     * <p>Performs two transformations:
     * <ul>
     *   <li>Prepends {@code #include <string>} and {@code #include <cstring>}
     *       to ensure string handling types are available without requiring the
     *       user to include them manually.</li>
     *   <li>For each line matching {@code // @mite}, wraps the immediately
     *       following function signature with {@code extern "C"} linkage if it
     *       is not already declared as such. This ensures Panama can locate the
     *       symbol by its unmangled name.</li>
     * </ul>
     *
     * <p>The processed source is written to a file with the same name as the
     * original inside the cache directory.
     *
     * @param cppFile the original {@code .cpp} source file
     * @return path to the preprocessed copy of the file inside the cache directory
     * @throws MiteException if the file cannot be read or the preprocessed copy
     *                       cannot be written
     */
    private Path preprocessAndCompile(Path cppFile) {
        try {
            List<String> lines = Files.readAllLines(cppFile);
            List<String> result = new ArrayList<>();
            result.add("#include <string>");
            result.add("#include <cstring>");
            result.add("");

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();

                if (trimmed.equals("// @mite") && i + 1 < lines.size()) {
                    String next = lines.get(i + 1).trim();
                    result.add(line);
                    if (next.startsWith("extern")) {
                        result.add(lines.get(i + 1));
                    } else {
                        result.add("extern \"C\" " + lines.get(i + 1));
                    }
                    i++;
                    continue;
                }
                result.add(line);
            }

            Path temp = cacheDir.resolve(cppFile.getFileName());
            Files.write(temp, result);
            return temp;
        } catch (IOException e) {
            throw new MiteException("Preprocessing error: " + cppFile, e);
        }
    }

    /**
     * Returns the last-modified timestamp of the given file in milliseconds.
     *
     * @param f the file to check
     * @return last-modified time in milliseconds, or {@code -1} if the value
     * cannot be read (e.g., the file does not exist)
     */
    private long lastModified(Path f) {
        try {
            return Files.getLastModifiedTime(f).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Holds a reference to a compiled shared library and the source file timestamp
     * at the time of compilation, used for cache invalidation.
     *
     * @param lib       path to the compiled shared library
     * @param timestamp last-modified timestamp of the source file in milliseconds,
     *                  or {@code -1} for inline-compiled entries
     */
    private record CachedLib(Path lib, long timestamp) {
    }
}