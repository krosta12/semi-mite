package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.memory.MiteArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiteContextTest {

    interface FakeClient {
    }

    @Test
    void putAndGetRoundTrip() {
        MiteContext ctx = new MiteContext();
        FakeClient instance = new FakeClient() {
        };
        ctx.put(FakeClient.class, instance);

        assertSame(instance, ctx.get(FakeClient.class));

        ctx.close();
    }

    @Test
    void getUnknownTypeThrows() {
        MiteContext ctx = new MiteContext();

        assertThrows(IllegalStateException.class, () -> ctx.get(FakeClient.class));

        ctx.close();
    }

    @Test
    void closeIsIdempotent() {
        MiteContext ctx = new MiteContext();

        assertDoesNotThrow(() -> {
            ctx.close();
            ctx.close();
        });

        ctx.close();
    }

    @Test
    void isClosedReturnsFalseInitially() {
        MiteContext ctx = new MiteContext();

        assertFalse(ctx.isClosed());
    }

    @Test
    void isClosedReturnsTrueAfterClose() {
        MiteContext ctx = new MiteContext();
        ctx.close();

        assertTrue(ctx.isClosed());
    }

    @Test
    void trackedArrayIsClosedWithContext() {
        MiteContext ctx = new MiteContext();
        MiteArray<Integer> arr = MiteArray.ofInts(4);
        ctx.track(arr);

        ctx.close();


        assertThrows(Exception.class, () -> arr.get(0));
    }

    @Test
    void trackReturnsTheSameArray() {
        MiteContext ctx = new MiteContext();
        MiteArray<Integer> arr = MiteArray.ofInts(1);
        MiteArray<Integer> returned = ctx.track(arr);

        assertSame(arr, returned);
        ctx.close();
    }
}
