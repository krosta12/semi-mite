package io.github.krosta12.semimite.compiler;

import java.io.File;
import java.util.List;

/**
 * Resolves the path to a native C++ compiler executable, either from explicit
 * configuration or by searching common compiler names on {@code PATH}.
 */
public class CompilerResolver {

    private static final List<String> CANDIDATES = List.of("g++", "clang++");

    /**
     * Resolves the compiler executable to use
     *
     * @param config the compiler configuration; if {@link CompilerConfig#compilerPath()}
     *               is set, it is returned as-is
     * @return the resolved compiler path or name
     * @throws IllegalStateException if {@code PATH} is not set or no known
     *                               compiler ({@code g++}, {@code clang++}) is found on it
     */
    public String resolve(CompilerConfig config) {
        if (config.compilerPath() != null && !config.compilerPath().isBlank()) {
            return config.compilerPath();
        }
        String path = System.getenv("PATH");
        if (path == null) {
            throw new IllegalStateException("PATH environment variable not set, cannot resolve compiler");
        }
        for (String candidate : CANDIDATES) {
            if (isOnPath(candidate, path)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Native compiler not found on PATH (tried: " + CANDIDATES + ")");
    }

    /**
     * Checks whether an executable exists on one of the directories in {@code path}.
     *
     * @param binary the executable name (without {@code .exe} suffix)
     * @param path   the {@code PATH} environment variable value
     * @return {@code true} if the executable was found on one of the directories
     */
    private boolean isOnPath(String binary, String path) {
        String exe = OsUtil.isWindows() ? binary + ".exe" : binary;
        for (String dir : path.split(File.pathSeparator)) {
            if (new File(dir, exe).isFile()) {
                return true;
            }
        }
        return false;
    }

}