package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.compiler.CompilerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class HotReloadWatcherTest {

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
    void closeOnNeverStartedWatcherDoesNotThrow() {
        HotReloadWatcher watcher = new HotReloadWatcher();
        assertDoesNotThrow(watcher::close);

        watcher.close();
    }

    @Test
    void closeIsIdempotent() {
        HotReloadWatcher watcher = new HotReloadWatcher();
        assertDoesNotThrow(() -> {
            watcher.close();
            watcher.close();
        });

        watcher.close();
    }

    @Test
    void startOnNonExistentDirectoryDoesNotThrow(@TempDir Path tempDir) {
        HotReloadWatcher watcher = new HotReloadWatcher();
        CompilerConfig config = new CompilerConfig(
                tempDir.resolve("does-not-exist"),
                tempDir.resolve("cache"),
                null, List.of(), 4);

        assertDoesNotThrow(() -> {
            watcher.start(config, lib -> {
            });
            watcher.close();
        });

        watcher.close();
    }
}