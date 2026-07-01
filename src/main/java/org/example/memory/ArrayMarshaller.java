package org.example.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;


public class ArrayMarshaller {

    public MemorySegment toSegment(Arena arena, float[] values) {
        MemorySegment segment = arena.allocate(ValueLayout.JAVA_FLOAT, values.length);
        MemorySegment.copy(values, 0, segment, ValueLayout.JAVA_FLOAT, 0, values.length);
        return segment;
    }

    public MemorySegment toSegment(Arena arena, int[] values) {
        MemorySegment segment = arena.allocate(ValueLayout.JAVA_INT, values.length);
        MemorySegment.copy(values, 0, segment, ValueLayout.JAVA_INT, 0, values.length);
        return segment;
    }

    public MemorySegment toSegment(Arena arena, double[] values) {
        MemorySegment segment = arena.allocate(ValueLayout.JAVA_DOUBLE, values.length);
        MemorySegment.copy(values, 0, segment, ValueLayout.JAVA_DOUBLE, 0, values.length);
        return segment;
    }

    public float[] toFloatArray(MemorySegment segment, int length) {
        float[] result = new float[length];
        MemorySegment.copy(segment, ValueLayout.JAVA_FLOAT, 0, result, 0, length);
        return result;
    }

    public int[] toIntArray(MemorySegment segment, int length) {
        int[] result = new int[length];
        MemorySegment.copy(segment, ValueLayout.JAVA_INT, 0, result, 0, length);
        return result;
    }

    public double[] toDoubleArray(MemorySegment segment, int length) {
        double[] result = new double[length];
        MemorySegment.copy(segment, ValueLayout.JAVA_DOUBLE, 0, result, 0, length);
        return result;
    }

}