package org.example.memory;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

class MiteArrayTest {

    @Test
    void allocateCreatesZeroedArray() {
        try (MiteArray arr = MiteArray.allocate(3)) {
            assertEquals(3, arr.length());
            assertEquals(0f, arr.get(0));
            assertEquals(0f, arr.get(1));
            assertEquals(0f, arr.get(2));
        }
    }

    @Test
    void ofCopiesValues() {
        float[] src = {1f, 2f, 3f};
        try (MiteArray arr = MiteArray.of(src)) {
            assertEquals(3, arr.length());
            assertEquals(1f, arr.get(0));
            assertEquals(2f, arr.get(1));
            assertEquals(3f, arr.get(2));
        }
    }

    @Test
    void setUpdatesValue() {
        try (MiteArray arr = MiteArray.allocate(2)) {
            arr.set(0, 7f);
            arr.set(1, 9f);

            assertEquals(7f, arr.get(0));
            assertEquals(9f, arr.get(1));
        }
    }

    @Test
    void toJavaArrayReturnsCorrectContent() {
        float[] src = {4f, 5f, 6f};
        try (MiteArray arr = MiteArray.of(src)) {
            float[] result = arr.toJavaArray();

            assertArrayEquals(src, result);
        }
    }

    @Test
    void ofDoesNotAliasOriginalArray() {
        float[] src = {1f, 2f, 3f};
        try (MiteArray arr = MiteArray.of(src)) {
            src[0] = 99f;

            assertEquals(1f, arr.get(0));
        }
    }

    @Test
    void segmentIsNonNull() {
        try (MiteArray arr = MiteArray.allocate(1)) {
            MemorySegment seg = arr.segment();

            assertNotNull(seg);
            assertNotEquals(MemorySegment.NULL, seg);
        }
    }

    @Test
    void getThrowsForNegativeIndex() {
        try (MiteArray arr = MiteArray.allocate(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> arr.get(-1));
        }
    }

    @Test
    void getThrowsForIndexAtLength() {
        try (MiteArray arr = MiteArray.allocate(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> arr.get(3));
        }
    }

    @Test
    void setThrowsForNegativeIndex() {
        try (MiteArray arr = MiteArray.allocate(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> arr.set(-1, 1f));
        }
    }

    @Test
    void setThrowsForIndexAtLength() {
        try (MiteArray arr = MiteArray.allocate(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> arr.set(3, 1f));
        }
    }

    @Test
    void closeDoesNotThrow() {
        MiteArray arr = MiteArray.allocate(2);
        assertDoesNotThrow(arr::close);
    }

    @Test
    void allocateZeroLength() {
        try (MiteArray arr = MiteArray.allocate(0)) {
            assertEquals(0, arr.length());
            assertArrayEquals(new float[0], arr.toJavaArray());
        }
    }
}
