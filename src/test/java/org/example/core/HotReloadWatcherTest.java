package org.example.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotReloadWatcherTest {

    @Test
    void closeOnNeverStartedWatcherDoesNotThrow() {
        HotReloadWatcher watcher = new HotReloadWatcher();

        assertDoesNotThrow(watcher::close);
    }

    @Test
    void closeIsIdempotent() {
        HotReloadWatcher watcher = new HotReloadWatcher();

        assertDoesNotThrow(() -> {
            watcher.close();
            watcher.close();
        });
    }
}
