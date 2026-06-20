package org.example.engine;

import java.lang.foreign.MemorySegment;

/**
 * Represents a resource that owns a region of off-heap native memory and can
 * expose it directly to the native layer via Project Panama.
 *
 * <p>Implementations allocate their memory outside the JVM heap using
 * {@link java.lang.foreign.Arena}, which means the data is not subject to
 * garbage collection or heap compaction. This allows C++ code to hold stable
 * pointers to the memory across multiple calls without risk of the address changing.
 *
 * <p>When a {@code NativeResource} is passed as an argument to
 * {@link CppEngine#execute(String, Object...)}, the marshalling layer detects it
 * and passes the underlying {@link MemorySegment} directly to the native function
 * as a pointer - no copying occurs. This is the mechanism behind the zero-copy
 * bridge described in {@link org.example.memory.MiteArray}.
 *
 * <p>{@code NativeResource} extends {@link AutoCloseable}, so implementations
 * should be used in try-with-resources blocks to ensure the underlying
 * {@link java.lang.foreign.Arena} is closed and the memory is released promptly:
 *
 * <pre>{@code
 * try (MiteArray data = MiteArray.ofFloats(1_000_000)) {
 *     engine.execute("process", data, data.length());
 * } // off-heap memory released here
 * }</pre>
 *
 * @see org.example.memory.MiteArray
 * @see org.example.memory.AbstractMiteArray
 */
public interface NativeResource extends AutoCloseable {
    /**
     * Returns the off-heap {@link MemorySegment} backing this resource.
     *
     * <p>The returned segment is valid only while this resource is open (i.e.,
     * before {@link #close()} is called). Accessing the segment after closing
     * the resource results in undefined behaviour.
     *
     * <p>The segment is passed directly to native functions without copying
     * when this object is used as an argument to {@link CppEngine#execute}.
     *
     * @return the native memory segment owned by this resource
     */
    MemorySegment segment();

    /**
     * Releases the off-heap memory owned by this resource.
     *
     * <p>After this method returns, the {@link MemorySegment} returned by
     * {@link #segment()} must not be accessed. Implementations typically
     * close the underlying {@link java.lang.foreign.Arena} here.
     */
    @Override
    void close();
}