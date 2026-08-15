package io.github.krosta12.semimite.compiler;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompilerResolverTest {

    private final CompilerResolver resolver = new CompilerResolver();

    @Test
    void returnsExplicitCompilerPathWhenSet() {
        CompilerConfig config = new CompilerConfig(
                Path.of("scripts"), Path.of("cache"), "/custom/g++", List.of(), 4);

        String resolved = resolver.resolve(config);

        assertEquals("/custom/g++", resolved);
    }

    @Test
    void fallsBackToPathScanWhenCompilerPathIsBlank() {
        CompilerConfig config = new CompilerConfig(
                Path.of("scripts"), Path.of("cache"), "   ", List.of(), 4);

        try {
            String result = resolver.resolve(config);
            assertNotNull(result);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("PATH") || e.getMessage().contains("compiler"));
        }
    }


    @Test
    void throwsWhenNoCompilerOnPath() {
        CompilerConfig config = new CompilerConfig(
                Path.of("scripts"), Path.of("cache"), null, List.of(), 4);

        String path = System.getenv("PATH");
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        boolean hasGpp = false;
        boolean hasClangpp = false;
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (new File(dir, win ? "g++.exe" : "g++").isFile()) hasGpp = true;
                if (new File(dir, win ? "clang++.exe" : "clang++").isFile()) hasClangpp = true;
            }
        }
        if (!hasGpp && !hasClangpp) {
            assertThrows(IllegalStateException.class, () -> resolver.resolve(config));
        } else {
            assertDoesNotThrow(() -> resolver.resolve(config));
        }
    }
}
