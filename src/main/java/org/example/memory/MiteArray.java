package org.example.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;


public class MiteArray<T> implements AutoCloseable {

    private static final int DEFAULT_ALIGNMENT = 8;

    @FunctionalInterface
    interface Getter<T> {
        T get(MemorySegment seg, int index);
    }

    @FunctionalInterface
    interface Setter<T> {
        void set(MemorySegment seg, int index, T value);
    }

    private final Arena arena;
    private final MemorySegment segment;
    private final int length;
    private final Getter<T> getter;
    private final Setter<T> setter;

    private MiteArray(int length, MemoryLayout layout, int alignment,
                      Getter<T> getter, Setter<T> setter) {
        this.length = length;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(layout.byteSize() * Math.max(length, 1), alignment);
        this.getter = getter;
        this.setter = setter;
    }

    public static MiteArray<Float> ofFloats(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_FLOAT, DEFAULT_ALIGNMENT,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_FLOAT, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_FLOAT, i, v));
    }

    public static MiteArray<Integer> ofInts(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_INT, DEFAULT_ALIGNMENT,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_INT, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_INT, i, v));
    }

    public static MiteArray<Double> ofDoubles(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_DOUBLE, DEFAULT_ALIGNMENT,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_DOUBLE, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_DOUBLE, i, v));
    }

    public static MiteArray<Long> ofLongs(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_LONG, DEFAULT_ALIGNMENT,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_LONG, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_LONG, i, v));
    }

    public static MiteArray<Byte> ofBytes(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_BYTE, DEFAULT_ALIGNMENT,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_BYTE, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_BYTE, i, v));
    }

    public static MiteArray<String> ofStrings(String... strings) {
        MiteArray<String> arr = new MiteArray<>(strings.length, ValueLayout.ADDRESS, DEFAULT_ALIGNMENT,
                (seg, i) -> {
                    MemorySegment ptr = seg.getAtIndex(ValueLayout.ADDRESS, i).reinterpret(4096);
                    long len = 0;
                    while (ptr.get(ValueLayout.JAVA_BYTE, len) != 0) len++;
                    return new String(ptr.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE));
                },
                (seg, i, v) -> {
                    throw new UnsupportedOperationException("MiteArray<String> is immutable after construction. Create a new MiteArray.ofStrings(...) with the desired values.");
                });
        for (int i = 0; i < strings.length; i++) {
            arr.segment.setAtIndex(ValueLayout.ADDRESS, i, arr.arena.allocateFrom(strings[i]));
        }
        return arr;
    }

    public T get(int index) {
        checkBounds(index);
        return getter.get(segment, index);
    }

    public void set(int index, T value) {
        checkBounds(index);
        setter.set(segment, index, value);
    }

    public int length() {
        return length;
    }

    public MemorySegment segment() {
        return segment;
    }

    private void checkBounds(int index) {
        if (index < 0 || index >= length)
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }

    @Override
    public void close() {
        arena.close();
    }

}