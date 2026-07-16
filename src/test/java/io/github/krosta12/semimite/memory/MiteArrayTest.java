package io.github.krosta12.semimite.memory;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

class MiteArrayTest {

    @Test
    void floatGetSet() {
        try (MiteArray<Float> arr = MiteArray.ofFloats(3)) {
            arr.set(0, 1.5f);
            arr.set(1, 2.5f);
            assertEquals(1.5f, arr.get(0));
            assertEquals(2.5f, arr.get(1));
            assertEquals(0f, arr.get(2));
        }
    }

    @Test
    void intGetSet() {
        try (MiteArray<Integer> arr = MiteArray.ofInts(2)) {
            arr.set(0, 42);
            arr.set(1, -7);
            assertEquals(42, arr.get(0));
            assertEquals(-7, arr.get(1));
        }
    }

    @Test
    void doubleGetSet() {
        try (MiteArray<Double> arr = MiteArray.ofDoubles(2)) {
            arr.set(0, 3.14);
            assertEquals(3.14, arr.get(0), 1e-9);
        }
    }

    @Test
    void longGetSet() {
        try (MiteArray<Long> arr = MiteArray.ofLongs(1)) {
            arr.set(0, Long.MAX_VALUE);
            assertEquals(Long.MAX_VALUE, arr.get(0));
        }
    }

    @Test
    void byteGetSet() {
        try (MiteArray<Byte> arr = MiteArray.ofBytes(2)) {
            arr.set(0, (byte) 0x7F);
            assertEquals((byte) 0x7F, arr.get(0));
        }
    }

    @Test
    void stringsLength() {
        try (MiteArray<String> arr = MiteArray.ofStrings("hello", "world")) {
            assertEquals(2, arr.length());
            assertEquals("hello", arr.get(0));
            assertEquals("world", arr.get(1));
        }
    }

    @Test
    void segmentNonNull() {
        try (MiteArray<Float> arr = MiteArray.ofFloats(1)) {
            assertNotNull(arr.segment());
            assertNotEquals(MemorySegment.NULL, arr.segment());
        }
    }

    @Test
    void boundsCheckNegative() {
        try (MiteArray<Float> arr = MiteArray.ofFloats(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> arr.get(-1));
        }
    }

    @Test
    void boundsCheckAtLength() {
        try (MiteArray<Float> arr = MiteArray.ofFloats(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> arr.get(3));
        }
    }

    @Test
    void closeDoesNotThrow() {
        assertDoesNotThrow(() -> MiteArray.ofFloats(2).close());
    }

    @Test
    void zeroLength() {
        try (MiteArray<Float> arr = MiteArray.ofFloats(0)) {
            assertEquals(0, arr.length());
        }
    }

}