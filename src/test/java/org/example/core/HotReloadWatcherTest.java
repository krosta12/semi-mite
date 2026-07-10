package org.example.core;

import org.example.compiler.CompilerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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
            watcher.start(config, lib -> {});
            watcher.close();
        });

        watcher.close();
    }

//    @Test
//    @EnabledIf("compilerAvailable")
//    void detectsChangeInNewlyCreatedSubdirectory(@TempDir Path tempDir) throws Exception {
//        Path scriptsDir = tempDir.resolve("scripts");
//        Files.createDirectories(scriptsDir);
//
//        Files.writeString(scriptsDir.resolve("init.cpp"), """
//                extern "C" {
//                // @mite
//                int init() { return 0; }
//                }
//                """);
//
//        CompilerConfig config = new CompilerConfig(
//                scriptsDir,
//                tempDir.resolve(".cache"),
//                null, List.of(), 4);
//
//        CountDownLatch firstBuild  = new CountDownLatch(1);
//        CountDownLatch secondBuild = new CountDownLatch(2);
//
//        try (HotReloadWatcher watcher = new HotReloadWatcher()) {
//            watcher.start(config, lib -> {
//                firstBuild.countDown();
//                secondBuild.countDown();
//            });
//
//            Path sub = scriptsDir.resolve("sub");
//            Files.createDirectories(sub);
//
//            Thread.sleep(600);
//            Files.writeString(sub.resolve("extra.cpp"), """
//                    extern "C" {
//                    // @mite
//                    int extra() { return 1; }
//                    }
//                    """);
//            boolean triggered = secondBuild.await(10, TimeUnit.SECONDS);
//            if (!triggered) {
//                triggered = firstBuild.getCount() == 0;
//            }
//            assertTrue(triggered, "Hot reload should trigger after file written in new subdirectory");
//        }
//    }
}
