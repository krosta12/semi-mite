package org.example.compiler;

import java.io.File;
import java.util.List;

public class CompilerResolver {

    private static final List<String> CANDIDATES = List.of("g++", "clang++");

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
        throw new IllegalStateException("Native compiler doesn't found on PATH (tried: " + CANDIDATES + ")");
    }

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