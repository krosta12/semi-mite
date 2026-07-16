package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.linker.NativeLibraryLinker.LinkedLibrary;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps a {@link LinkedLibrary} to make it safe to close during hot reload:
 * tracks the number of in-flight native calls and only closes the underlying
 * library once all calls have completed and retirement has been requested.
 */
public final class ManagedLibrary {

    private static final int CLOSED = -1;

    private final LinkedLibrary library;
    private final AtomicInteger active = new AtomicInteger(0);
    private volatile boolean retireRequested = false;

    /**
     * Creates a managed wrapper around a linked native library.
     *
     * @param library the linked library to manage
     */
    public ManagedLibrary(LinkedLibrary library) {
        this.library = library;
    }

    /**
     * Returns the map of native function handles exposed by the underlying library.
     *
     * @return the handle map, keyed by function name
     */
    public Map<String, MethodHandle> handles() {
        return library.handles();
    }

    /**
     * Attempts to acquire the library for a call, incrementing the active-call count.
     *
     * @return {@code true} if acquired successfully; {@code false} if the
     *         library has already been closed
     */
    public boolean acquire() {
        while (true) {
            int c = active.get();
            if (c == CLOSED) return false;
            if (active.compareAndSet(c, c + 1)) return true;
        }
    }

    /**
     * Releases the library after a call completes, decrementing the
     * active-call count. If the count reaches zero and retirement has been
     * requested, the library is closed.
     */
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

    /**
     * Marks the library for retirement. If there are no active calls, it is
     * closed immediately; otherwise it will be closed once the last active
     * call completes.
     */
    public void retire() {
        retireRequested = true;
        tryCloseNow();
    }

    /**
     * Atomically closes the underlying library if there are no active calls.
     */
    private void tryCloseNow() {
        if (active.compareAndSet(0, CLOSED)) {
            try {
                library.close();
            } catch (Exception ignored) {
            }
        }
    }
}