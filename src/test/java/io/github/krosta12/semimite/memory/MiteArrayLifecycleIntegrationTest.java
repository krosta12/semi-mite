package io.github.krosta12.semimite.memory;

import io.github.krosta12.semimite.core.MiteContext;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;


class MiteArrayLifecycleIntegrationTest {


    @Test
    void floatArrayReadWriteAllElements() {
        try (MiteArray<Float> arr = MiteArray.ofFloats(5)) {
            assertEquals(5, arr.length());
            for (int i = 0; i < 5; i++) arr.set(i, (float) i * 1.5f);
            for (int i = 0; i < 5; i++) assertEquals(i * 1.5f, arr.get(i), 1e-6f);
        }
    }

    @Test
    void intArrayReadWriteAllElements() {
        try (MiteArray<Integer> arr = MiteArray.ofInts(4)) {
            arr.set(0, 100);
            arr.set(1, 200);
            arr.set(2, 300);
            arr.set(3, 400);
            assertEquals(100, arr.get(0));
            assertEquals(400, arr.get(3));
        }
    }

    @Test
    void doubleArrayReadWriteAllElements() {
        try (MiteArray<Double> arr = MiteArray.ofDoubles(3)) {
            arr.set(0, Math.PI);
            arr.set(1, Math.E);
            arr.set(2, Math.sqrt(2));
            assertEquals(Math.PI, arr.get(0), 1e-15);
            assertEquals(Math.E, arr.get(1), 1e-15);
        }
    }

    @Test
    void longArrayReadWriteAllElements() {
        try (MiteArray<Long> arr = MiteArray.ofLongs(3)) {
            arr.set(0, Long.MAX_VALUE);
            arr.set(1, Long.MIN_VALUE);
            arr.set(2, 0L);
            assertEquals(Long.MAX_VALUE, arr.get(0));
            assertEquals(Long.MIN_VALUE, arr.get(1));
            assertEquals(0L, arr.get(2));
        }
    }

    @Test
    void byteArrayReadWriteAllElements() {
        try (MiteArray<Byte> arr = MiteArray.ofBytes(4)) {
            arr.set(0, (byte) 0xFF);
            arr.set(1, (byte) 0x00);
            arr.set(2, (byte) 127);
            arr.set(3, (byte) -128);
            assertEquals((byte) 0xFF, arr.get(0));
            assertEquals((byte) -128, arr.get(3));
        }
    }


    @Test
    void stringArrayCanBeReadBack() {
        try (MiteArray<String> arr = MiteArray.ofStrings("alpha", "beta", "gamma")) {
            assertEquals(3, arr.length());
            assertEquals("alpha", arr.get(0));
            assertEquals("beta", arr.get(1));
            assertEquals("gamma", arr.get(2));
        }
    }

    @Test
    void stringArraySetThrowsUnsupportedOperation() {
        try (MiteArray<String> arr = MiteArray.ofStrings("a", "b")) {
            assertThrows(UnsupportedOperationException.class, () -> arr.set(0, "c"),
                    "MiteArray<String> must be immutable after construction");
        }
    }

    @Test
    void stringArrayWithEmptyStrings() {
        try (MiteArray<String> arr = MiteArray.ofStrings("", "hello", "")) {
            assertEquals("", arr.get(0));
            assertEquals("hello", arr.get(1));
            assertEquals("", arr.get(2));
        }
    }


    @Test
    void negativeIndexThrowsIndexOutOfBounds() {
        try (MiteArray<Integer> arr = MiteArray.ofInts(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> arr.get(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> arr.set(-1, 0));
        }
    }

    @Test
    void indexAtLengthThrowsIndexOutOfBounds() {
        try (MiteArray<Integer> arr = MiteArray.ofInts(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> arr.get(3));
            assertThrows(IndexOutOfBoundsException.class, () -> arr.set(3, 0));
        }
    }

    @Test
    void lastValidIndexWorks() {
        try (MiteArray<Integer> arr = MiteArray.ofInts(3)) {
            arr.set(2, 999);
            assertEquals(999, arr.get(2));
        }
    }

    @Test
    void arrayOfLengthOneWorks() {
        try (MiteArray<Float> arr = MiteArray.ofFloats(1)) {
            arr.set(0, 42f);
            assertEquals(42f, arr.get(0), 1e-6f);
            assertThrows(IndexOutOfBoundsException.class, () -> arr.get(1));
        }
    }


    @Test
    void segmentIsNotNullAndHasCorrectByteSize() {
        try (MiteArray<Integer> arr = MiteArray.ofInts(4)) {
            MemorySegment seg = arr.segment();
            assertNotNull(seg);
            assertEquals(4 * 4L, seg.byteSize(), "int[4] must be 16 bytes");
        }
    }

    @Test
    void segmentOfFloatArrayHasCorrectSize() {
        try (MiteArray<Float> arr = MiteArray.ofFloats(8)) {
            assertEquals(8 * 4L, arr.segment().byteSize());
        }
    }

    @Test
    void segmentOfDoubleArrayHasCorrectSize() {
        try (MiteArray<Double> arr = MiteArray.ofDoubles(3)) {
            assertEquals(3 * 8L, arr.segment().byteSize());
        }
    }

    @Test
    void segmentOfLongArrayHasCorrectSize() {
        try (MiteArray<Long> arr = MiteArray.ofLongs(5)) {
            assertEquals(5 * 8L, arr.segment().byteSize());
        }
    }


    @Test
    void accessAfterCloseThrows() {
        MiteArray<Integer> arr = MiteArray.ofInts(4);
        arr.set(0, 1);
        arr.close();

        assertThrows(Exception.class, () -> arr.get(0),
                "get() on closed array must throw");
        assertThrows(Exception.class, () -> arr.set(0, 2),
                "set() on closed array must throw");
    }


    @Test
    void contextCloseReleasesAllTrackedArrays() {
        MiteContext ctx = new MiteContext();

        MiteArray<Integer> ints = ctx.track(MiteArray.ofInts(4));
        MiteArray<Float> floats = ctx.track(MiteArray.ofFloats(4));
        MiteArray<Double> doubles = ctx.track(MiteArray.ofDoubles(2));

        ints.set(0, 42);
        floats.set(0, 3.14f);
        doubles.set(0, 2.71);

        assertEquals(42, ints.get(0));

        ctx.close();

        assertThrows(Exception.class, () -> ints.get(0));
        assertThrows(Exception.class, () -> floats.get(0));
        assertThrows(Exception.class, () -> doubles.get(0));
    }


    @Test
    void contextTrackReturnsSameArrayInstance() {
        MiteContext ctx = new MiteContext();
        MiteArray<Long> original = MiteArray.ofLongs(3);
        MiteArray<Long> tracked = ctx.track(original);

        assertSame(original, tracked);
        ctx.close();
    }


    @Test
    void contextCanTrackMultipleArraysOfSameType() {
        MiteContext ctx = new MiteContext();
        MiteArray<Integer> arr1 = ctx.track(MiteArray.ofInts(2));
        MiteArray<Integer> arr2 = ctx.track(MiteArray.ofInts(3));

        arr1.set(0, 1);
        arr2.set(0, 2);

        assertEquals(1, arr1.get(0));
        assertEquals(2, arr2.get(0));

        ctx.close();
    }
}
