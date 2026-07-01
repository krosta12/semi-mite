package org.example.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;


public class MiteArray implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment segment;
    private final int length;

    private MiteArray(Arena arena, MemorySegment segment, int length) {
        this.arena = arena;
        this.segment = segment;
        this.length = length;
    }

    public static MiteArray allocate(int length) {
        Arena arena = Arena.ofShared();
        MemorySegment segment = arena.allocate(ValueLayout.JAVA_FLOAT, length);
        return new MiteArray(arena, segment, length);
    }

    public static MiteArray of(float[] values) {
        MiteArray array = allocate(values.length);
        MemorySegment.copy(values, 0, array.segment, ValueLayout.JAVA_FLOAT, 0, values.length);
        return array;
    }

    public float get(int index) {
        checkBounds(index);
        return segment.getAtIndex(ValueLayout.JAVA_FLOAT, index);
    }

    public void set(int index, float value) {
        checkBounds(index);
        segment.setAtIndex(ValueLayout.JAVA_FLOAT, index, value);
    }

    public float[] toJavaArray() {
        float[] result = new float[length];
        MemorySegment.copy(segment, ValueLayout.JAVA_FLOAT, 0, result, 0, length);
        return result;
    }

    public int length() {
        return length;
    }

    public MemorySegment segment() {
        return segment;
    }

    private void checkBounds(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for len: " + length);
        }
    }

    @Override
    public void close() {
        arena.close();
    }

}