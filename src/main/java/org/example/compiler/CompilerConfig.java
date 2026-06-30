package org.example.compiler;

import java.nio.file.Path;
import java.util.List;

public record CompilerConfig(
        Path scriptsDir,
        Path cacheDir,
        String compilerPath,
        List<String> compilerFlags,
        int alignmentBytes
) {

    public static CompilerConfig defaults() {
        return new CompilerConfig(
                Path.of("cppScripts"),
                Path.of(".mite-cache"),
                null,
                List.of("-O2"),
                4
        );
    }

}