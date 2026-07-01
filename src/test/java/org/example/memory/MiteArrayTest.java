package org.example.memory;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

class MiteArrayTest {

    @Test
    void getSetRoundTrip() {
        try (MiteArray array = MiteArray.allocate(3)) {
            array.set(0, 1f);
            array.set(1, 2f);
            array.set(2, 3f);

            assertEquals(1f, array.get(0));
            assertEquals(2f, array.get(1));
            assertEquals(3f, array.get(2));
        }
    }

    @Test
    void ofCopiesJavaArray() {
        try (MiteArray array = MiteArray.of(new float[]{4f, 5f, 6f})) {
            assertArrayEquals(new float[]{4f, 5f, 6f}, array.toJavaArray());
        }
    }

    @Test
    void throwsOnOutOfBounds() {
        try (MiteArray array = MiteArray.allocate(2)) {
            assertThrows(IndexOutOfBoundsException.class, () -> array.get(5));
        }
    }

    @Test
    void arrayMarshallerRoundTripsFloatArray() {
        ArrayMarshaller marshaller = new ArrayMarshaller();
        float[] original = {1.1f, 2.2f, 3.3f};

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = marshaller.toSegment(arena, original);
            float[] result = marshaller.toFloatArray(segment, original.length);

            assertArrayEquals(original, result);
        }
    }

}