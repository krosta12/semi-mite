package org.example.linker;

import org.example.linker.NativeLibraryLinker.LinkedLibrary;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ManagedLibraryTest {

    private LinkedLibrary newLibrary() {
        return new LinkedLibrary(Map.of(), Arena.ofShared());
    }

    @Test
    void acquireReturnsTrueWhileOpen() {
        ManagedLibrary lib = new ManagedLibrary(newLibrary());
        assertTrue(lib.acquire());
        assertTrue(lib.acquire());
        lib.release();
        lib.release();
    }

    @Test
    void handlesDelegatesToUnderlyingLibrary() throws Throwable {
        MethodHandle h = MethodHandles.constant(int.class, 42);
        LinkedLibrary linked = new LinkedLibrary(Map.of("answer", h), Arena.ofShared());
        ManagedLibrary lib = new ManagedLibrary(linked);

        assertSame(h, lib.handles().get("answer"));
        assertEquals(42, (int) lib.handles().get("answer").invoke());
    }

    @Test
    void retireWithNoActiveUsersClosesImmediately() {
        LinkedLibrary linked = newLibrary();
        ManagedLibrary lib = new ManagedLibrary(linked);

        lib.retire();

        assertFalse(lib.acquire(), "library must be closed after retire with 0 active users");
        assertThrows(IllegalStateException.class,
                () -> linked.arena().allocate(ValueLayout.JAVA_INT),
                "underlying arena should be closed once retired");
    }

    @Test
    void retireWithActiveUserDefersCloseUntilRelease() {
        LinkedLibrary linked = newLibrary();
        ManagedLibrary lib = new ManagedLibrary(linked);

        assertTrue(lib.acquire());
        lib.retire();

        assertDoesNotThrow(() -> linked.arena().allocate(ValueLayout.JAVA_INT));

        lib.release();

        assertThrows(IllegalStateException.class,
                () -> linked.arena().allocate(ValueLayout.JAVA_INT),
                "arena should close once the last active user releases");
    }

    @Test
    void acquireAfterRetireAlwaysReturnsFalse() {
        ManagedLibrary lib = new ManagedLibrary(newLibrary());
        lib.retire();

        assertFalse(lib.acquire());
        assertFalse(lib.acquire());
    }

    @Test
    void releaseWithoutAcquireIsNoop() {
        LinkedLibrary linked = newLibrary();
        ManagedLibrary lib = new ManagedLibrary(linked);

        assertDoesNotThrow(lib::release);
        assertDoesNotThrow(lib::release);

        assertTrue(lib.acquire());
        lib.release();
    }

    @Test
    void doubleRetireIsSafe() {
        ManagedLibrary lib = new ManagedLibrary(newLibrary());
        lib.retire();
        assertDoesNotThrow(lib::retire);
    }

    @Test
    void concurrentAcquireReleaseNeverCorruptsStateAndRetireEventuallySucceeds() throws Exception {
        ManagedLibrary lib = new ManagedLibrary(newLibrary());
        int threads = 8;
        int iterations = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicInteger acquiredOk = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        if (lib.acquire()) {
                            acquiredOk.incrementAndGet();
                            lib.release();
                        }
                    }
                } catch (Throwable e) {
                    failed.set(true);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        pool.close();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertFalse(failed.get());
        assertTrue(acquiredOk.get() > 0);

        lib.retire();
        assertFalse(lib.acquire(), "retire after all workers finished must close the library");
    }
}