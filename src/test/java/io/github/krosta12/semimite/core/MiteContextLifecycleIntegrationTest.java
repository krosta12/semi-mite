package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.linker.ManagedLibrary;
import io.github.krosta12.semimite.linker.NativeLibraryLinker.LinkedLibrary;
import io.github.krosta12.semimite.memory.MiteArray;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


class MiteContextLifecycleIntegrationTest {

    interface SomeClient {
    }

    private ManagedLibrary newManagedLibrary() {
        return new ManagedLibrary(new LinkedLibrary(Map.of(), Arena.ofShared()));
    }

    @Test
    void closeRetiresAllManagedLibraries() {
        MiteContext ctx = new MiteContext();
        ManagedLibrary lib1 = newManagedLibrary();
        ManagedLibrary lib2 = newManagedLibrary();
        ctx.addLibrary(lib1);
        ctx.addLibrary(lib2);

        ctx.close();

        assertFalse(lib1.acquire(), "lib1 must be retired after context close");
        assertFalse(lib2.acquire(), "lib2 must be retired after context close");
    }

    /**
     * Each tracked MiteArray must be cloused after closing the context
     */
    @Test
    void closeReleasesAllTrackedArrays() {
        MiteContext ctx = new MiteContext();
        MiteArray<Integer> arr1 = ctx.track(MiteArray.ofInts(4));
        MiteArray<Float> arr2 = ctx.track(MiteArray.ofFloats(8));

        ctx.close();

        assertThrows(Exception.class, () -> arr1.get(0), "arr1 must be closed");
        assertThrows(Exception.class, () -> arr2.get(0), "arr2 must be closed");
    }

    @Test
    void doubleCloseIsIdempotentAndSafe() {
        MiteContext ctx = new MiteContext();
        ManagedLibrary lib = newManagedLibrary();
        ctx.addLibrary(lib);
        ctx.track(MiteArray.ofInts(2));

        ctx.close();
        assertDoesNotThrow(ctx::close, "second close must not throw");
        assertTrue(ctx.isClosed());
    }

    /**
     * Context closing should kill the bootstrap if it was linked
     */
    @Test
    void closeAlsoClosesLinkedBootstrap() {
        MiteContext ctx = new MiteContext();
        int[] closedCount = {0};

        MiteBootstrap fakeBootstrap = new MiteBootstrap() {
            @Override
            public void close() {
                closedCount[0]++;
            }
        };

        ctx.setBootstrap(fakeBootstrap);
        ctx.close();

        assertEquals(1, closedCount[0], "bootstrap.close() should be called exactly once");
    }


    @Test
    void putAndGetMultipleClientsIsolatedFromEachOther() {
        interface Alpha {
        }
        interface Beta {
        }

        MiteContext ctx = new MiteContext();
        Alpha a = new Alpha() {
        };
        Beta b = new Beta() {
        };

        ctx.put(Alpha.class, a);
        ctx.put(Beta.class, b);

        assertSame(a, ctx.get(Alpha.class));
        assertSame(b, ctx.get(Beta.class));

        ctx.close();
    }

    /**
     * get() for a missing client should throw an exception with the class name in the message
     */
    @Test
    void getMissingClientThrowsWithClassName() {
        MiteContext ctx = new MiteContext();
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> ctx.get(SomeClient.class));
        assertTrue(ex.getMessage().contains(SomeClient.class.getName()),
                "exception message must include the missing class name");
        ctx.close();
    }

    /**
     * track() returns the same instance for fluent usage, allowing method chaining
     */
    @Test
    void trackReturnsSameInstanceForFluentUsage() {
        MiteContext ctx = new MiteContext();
        MiteArray<Long> arr = MiteArray.ofLongs(3);
        MiteArray<Long> returned = ctx.track(arr);

        assertSame(arr, returned);
        ctx.close();
    }

    /**
     * Context clousing should not kill the process
     */
    @Test
    void libraryRetiredByContextWaitsForActiveAcquire() {
        MiteContext ctx = new MiteContext();
        LinkedLibrary linked = new LinkedLibrary(Map.of(), Arena.ofShared());
        ManagedLibrary lib = new ManagedLibrary(linked);
        ctx.addLibrary(lib);

        assertTrue(lib.acquire(), "acquire before retire must succeed");

        ctx.close();

        assertDoesNotThrow(() -> linked.arena().allocate(ValueLayout.JAVA_INT),
                "arena must still be alive while there is an active acquire");

        lib.release();
        assertThrows(IllegalStateException.class,
                () -> linked.arena().allocate(ValueLayout.JAVA_INT),
                "arena must close once last acquire is released");
    }
}
