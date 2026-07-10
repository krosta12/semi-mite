package org.example.linker;

import org.example.linker.NativeLibraryLinker.LinkedLibrary;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class ManagedLibrary {

    private static final int CLOSED = -1;

    private final LinkedLibrary library;
    private final AtomicInteger active = new AtomicInteger(0);
    private volatile boolean retireRequested = false;

    public ManagedLibrary(LinkedLibrary library) {
        this.library = library;
    }

    public Map<String, MethodHandle> handles() {
        return library.handles();
    }

    public boolean acquire() {
        while (true) {
            int c = active.get();
            if (c == CLOSED) return false;
            if (active.compareAndSet(c, c + 1)) return true;
        }
    }

    public void release() {
        while (true) {
            int c = active.get();
            if (c <= 0) return;
            int next = c - 1;
            if (active.compareAndSet(c, next)) {
                if (next == 0 && retireRequested) {
                    tryCloseNow();
                }
                return;
            }
        }
    }

    public void retire() {
        retireRequested = true;
        tryCloseNow();
    }

    private void tryCloseNow() {
        if (active.compareAndSet(0, CLOSED)) {
            try {
                library.close();
            } catch (Exception ignored) {
            }
        }
    }
}