package org.example.config;

/**
 * Process-wide static holder for semi-mite runtime configuration values
 * that must be accessible throughout the framework without Spring dependency injection.
 *
 * <p>Initialized once during application startup by
 * {@link MiteAutoConfiguration#initGlobalContext()} before any native calls are made.
 * After initialization the values are effectively immutable for the lifetime of
 * the process - {@link #init(int)} should not be called again after startup.
 *
 * <p>Currently holds a single value:
 * <ul>
 *   <li>{@code alignmentBytes} - the memory alignment boundary used by the
 *       marshalling layer when calculating native struct field offsets. Defaults
 *       to {@code 4}. Set to {@code 32} for AVX2/AVX-512 workloads that require
 *       256-bit aligned memory.</li>
 * </ul>
 *
 * <p>The field is declared {@code volatile} to ensure visibility across threads
 * in environments where the initializing thread and the consuming thread may differ.
 *
 * @see MiteAutoConfiguration
 * @see MiteProperties
 */
public class MiteContext {

    /**
     * Memory alignment in bytes applied during native struct layout calculations.
     * Volatile to ensure cross-thread visibility after initialization.
     */
    private static volatile int alignmentBytes = 4;

    /**
     * Initializes the global semi-mite context with the configured alignment value.
     *
     * <p>Must be called exactly once, during application startup, before any
     * native marshalling operations are performed. Subsequent calls will overwrite
     * the previously set value.
     *
     * @param bytes the memory alignment boundary in bytes; typical values are
     *              {@code 4} (default, general-purpose) or {@code 32} (AVX2/AVX-512)
     */
    public static void init(int bytes) {
        alignmentBytes = bytes;
    }

    /**
     * Returns the memory alignment boundary currently configured for the process.
     *
     * <p>Used by the marshalling layer to align struct field offsets in native
     * memory so that C++ struct layout matches the Java-side memory map.
     *
     * @return alignment in bytes; {@code 4} by default
     */
    public static int getAlignmentBytes() {
        return alignmentBytes;
    }
}