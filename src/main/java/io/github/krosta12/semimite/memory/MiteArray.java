package io.github.krosta12.semimite.memory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;


/**
 * A type-safe wrapper around a fixed-length off-heap native array
 * (float/int/double/long/byte/String), backed by its own {@link Arena}.
 *
 * @param <T> the element type
 */
public class MiteArray<T> implements AutoCloseable {

    /**
     * Reads an element of type {@code T} from a memory segment at a given index.
     *
     * @param <T> the element type
     */
    @FunctionalInterface
    interface Getter<T> {
        T get(MemorySegment seg, int index);
    }

    /**
     * Writes an element of type {@code T} into a memory segment at a given index.
     *
     * @param <T> the element type
     */
    @FunctionalInterface
    interface Setter<T> {
        void set(MemorySegment seg, int index, T value);
    }

    private final Arena arena;
    private final MemorySegment segment;
    private final int length;
    private final Getter<T> getter;
    private final Setter<T> setter;

    /**
     * Allocates a new off-heap array.
     *
     * @param length the number of elements
     * @param layout the native value layout of a single element
     * @param getter reads an element from the segment
     * @param setter writes an element into the segment
     */
    private MiteArray(int length, ValueLayout layout,
                      Getter<T> getter, Setter<T> setter) {
        this.length = length;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(layout.byteSize() * Math.max(length, 1), layout.byteAlignment());
        this.getter = getter;
        this.setter = setter;
    }

    /**
     * Creates an off-heap array of {@code float}.
     *
     * @param length the number of elements
     * @return a new {@code MiteArray<Float>}
     */
    public static MiteArray<Float> ofFloats(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_FLOAT,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_FLOAT, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_FLOAT, i, v));
    }

    /**
     * Creates an off-heap array of {@code int}.
     *
     * @param length the number of elements
     * @return a new {@code MiteArray<Integer>}
     */
    public static MiteArray<Integer> ofInts(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_INT,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_INT, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_INT, i, v));
    }

    /**
     * Creates an off-heap array of {@code double}.
     *
     * @param length the number of elements
     * @return a new {@code MiteArray<Double>}
     */
    public static MiteArray<Double> ofDoubles(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_DOUBLE,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_DOUBLE, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_DOUBLE, i, v));
    }

    /**
     * Creates an off-heap array of {@code long}.
     *
     * @param length the number of elements
     * @return a new {@code MiteArray<Long>}
     */
    public static MiteArray<Long> ofLongs(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_LONG,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_LONG, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_LONG, i, v));
    }

    /**
     * Creates an off-heap array of {@code byte}.
     *
     * @param length the number of elements
     * @return a new {@code MiteArray<Byte>}
     */
    public static MiteArray<Byte> ofBytes(int length) {
        return new MiteArray<>(length, ValueLayout.JAVA_BYTE,
                (seg, i) -> seg.getAtIndex(ValueLayout.JAVA_BYTE, i),
                (seg, i, v) -> seg.setAtIndex(ValueLayout.JAVA_BYTE, i, v));
    }

    /**
     * Creates an off-heap, immutable array of C strings from the given Java strings.
     *
     * @param strings the strings to store
     * @return a new {@code MiteArray<String>}; calling {@link #set} on it always throws
     */
    public static MiteArray<String> ofStrings(String... strings) {
        MiteArray<String> arr = new MiteArray<>(strings.length, ValueLayout.ADDRESS,
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

    /**
     * Returns the element at the given index.
     *
     * @param index the element index
     * @return the element value
     * @throws IndexOutOfBoundsException if {@code index} is out of bounds
     */
    public T get(int index) {
        checkBounds(index);
        return getter.get(segment, index);
    }

    /**
     * Sets the element at the given index.
     *
     * @param index the element index
     * @param value the value to store
     * @throws IndexOutOfBoundsException      if {@code index} is out of bounds
     * @throws UnsupportedOperationException  if this is a {@code MiteArray<String>} (immutable)
     */
    public void set(int index, T value) {
        checkBounds(index);
        setter.set(segment, index, value);
    }

    /**
     * Returns the number of elements in this array.
     *
     * @return the array length
     */
    public int length() {
        return length;
    }

    /**
     * Returns the underlying native memory segment, for passing directly
     * into native calls.
     *
     * @return the backing memory segment
     */
    public MemorySegment segment() {
        return segment;
    }

    /**
     * Validates that an index is within {@code [0, length)}.
     *
     * @param index the index to check
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    private void checkBounds(int index) {
        if (index < 0 || index >= length)
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }

    /**
     * Closes the backing arena, releasing the array's native memory.
     */
    @Override
    public void close() {
        arena.close();
    }

}