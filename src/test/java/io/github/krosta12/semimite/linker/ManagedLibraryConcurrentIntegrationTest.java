package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.linker.NativeLibraryLinker.LinkedLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;


class ManagedLibraryConcurrentIntegrationTest {

    private LinkedLibrary newLinked() {
        return new LinkedLibrary(Map.of(), Arena.ofShared());
    }

    private ManagedLibrary newLib() {
        return new ManagedLibrary(newLinked());
    }


    @Test
    void freshLibraryAcceptsAcquire() {
        ManagedLibrary lib = newLib();
        assertTrue(lib.acquire());
        lib.release();
    }

    @Test
    void retiredLibraryRejectsAcquire() {
        ManagedLibrary lib = newLib();
        lib.retire();
        assertFalse(lib.acquire());
    }

    @Test
    void multipleAcquiresAllowed() {
        ManagedLibrary lib = newLib();
        assertTrue(lib.acquire());
        assertTrue(lib.acquire());
        assertTrue(lib.acquire());
        lib.release();
        lib.release();
        lib.release();
    }

    @Test
    void retireDeferred_closesAfterLastRelease() {
        LinkedLibrary linked = newLinked();
        ManagedLibrary lib = new ManagedLibrary(linked);

        lib.acquire();
        lib.retire();


        assertDoesNotThrow(() -> linked.arena().allocate(ValueLayout.JAVA_INT));

        lib.release();

        assertThrows(IllegalStateException.class,
                () -> linked.arena().allocate(ValueLayout.JAVA_INT));
    }

    @Test
    void retireWithZeroActive_closesImmediately() {
        LinkedLibrary linked = newLinked();
        ManagedLibrary lib = new ManagedLibrary(linked);
        lib.retire();

        assertThrows(IllegalStateException.class,
                () -> linked.arena().allocate(ValueLayout.JAVA_INT));
    }

    @Test
    void doubleRetireIsSafe() {
        ManagedLibrary lib = newLib();
        lib.retire();
        assertDoesNotThrow(lib::retire);
    }

    @Test
    void releaseWithoutAcquireIsNoop() {
        ManagedLibrary lib = newLib();
        assertDoesNotThrow(lib::release);
        assertDoesNotThrow(lib::release);

        assertTrue(lib.acquire());
        lib.release();
    }


    /**
     * Simulation of a hot-swap scenario: the new library becomes active, and the old one is closed
     * only after all in-flight calls have completed.
     */
    @Test
    @Timeout(10)
    void hotSwapScenario_oldLibraryClosedOnlyAfterInFlightCallsComplete() throws Exception {
        LinkedLibrary oldLinked = newLinked();
        ManagedLibrary oldLib = new ManagedLibrary(oldLinked);
        ManagedLibrary newLib = newLib();

        AtomicReference<ManagedLibrary> current = new AtomicReference<>(oldLib);


        assertTrue(oldLib.acquire());

        current.set(newLib);
        oldLib.retire();

        assertDoesNotThrow(() -> oldLinked.arena().allocate(ValueLayout.JAVA_INT));

        assertTrue(newLib.acquire());
        newLib.release();

        oldLib.release();

        assertThrows(IllegalStateException.class,
                () -> oldLinked.arena().allocate(ValueLayout.JAVA_INT));
    }


    @Test
    @Timeout(30)
    void concurrentAcquireRelease_noStateCorruption() throws Exception {
        ManagedLibrary lib = newLib();
        int threads = 8;
        int iterations = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        if (lib.acquire()) {
                            successCount.incrementAndGet();
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
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

        assertFalse(failed.get(), "no thread must throw during concurrent acquire/release");
        assertTrue(successCount.get() > 0, "at least one acquire must succeed");

        lib.retire();
        assertFalse(lib.acquire(), "library must be closed after retire");
    }


    @Test
    @Timeout(30)
    void concurrentRetireWhileActiveCallers_libraryEventuallyCloses() throws Exception {
        LinkedLibrary linked = newLinked();
        ManagedLibrary lib = new ManagedLibrary(linked);

        int threads = 4;
        int iterations = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        if (lib.acquire()) {
                            Thread.yield();
                            lib.release();
                        }
                    }
                } catch (Throwable e) {
                    failed.set(true);
                }
            });
        }

        pool.submit(() -> {
            try {
                start.await();
                Thread.sleep(1);
                lib.retire();
            } catch (Throwable e) {
                failed.set(true);
            }
        });

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

        assertFalse(failed.get(), "no thread must throw");

        assertFalse(lib.acquire(), "library must be closed after all releases complete");
    }


    @Test
    void handlesMapDelegatedCorrectly() {
        var handles = Map.<String, java.lang.invoke.MethodHandle>of();
        LinkedLibrary linked = new LinkedLibrary(handles, Arena.ofShared());
        ManagedLibrary lib = new ManagedLibrary(linked);

        assertSame(handles, lib.handles());

        lib.acquire();
        lib.retire();
        lib.release();
    }
}
