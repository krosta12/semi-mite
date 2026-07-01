package org.example.core;

import org.example.client.MiteClient;
import org.example.compiler.CompilerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiteBootstrapIntegrationTest {

    @MiteClient
    public interface CalcClient {
        int add(int a, int b);
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
    void compilesLinksAndCallsNativeFunction(@TempDir(cleanup = CleanupMode.ON_SUCCESS) Path tempDir) throws Exception {
        Path scriptsDir = tempDir.resolve("cppScripts");
        Files.createDirectories(scriptsDir);
        Files.writeString(scriptsDir.resolve("calc.cpp"), """
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
                java.util.List.of("-O2"),
                4
        );

        MiteBootstrap bootstrap = new MiteBootstrap();
        try {
            MiteContext context = bootstrap.start("org.example.core", config);
            CalcClient client = context.get(CalcClient.class);

            assertEquals(7, client.add(3, 4));
        } finally {
            bootstrap.close();
        }
    }

}