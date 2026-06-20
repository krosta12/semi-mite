package org.example.engine;

/**
 * Primary API for invoking native C++ functions from Java code within the semi-mite framework.
 *
 * <p>Implementations of this interface handle the full lifecycle of a native call:
 * <ol>
 *   <li>Resolving the target function by name from the {@link org.example.scanner.FunctionRegistry}</li>
 *   <li>Compiling (or loading from cache) the shared library that contains the function</li>
 *   <li>Marshalling Java arguments into off-heap native memory</li>
 *   <li>Invoking the function via Project Panama's downcall linker</li>
 *   <li>Copying modified argument values back to the original Java objects</li>
 *   <li>Unmarshalling the return value back to a Java type</li>
 * </ol>
 *
 * <p>The default implementation is {@link DefaultCppEngine}, registered automatically
 * by {@link org.example.config.MiteAutoConfiguration}.
 *
 * <p>Functions must be marked with {@code // @mite} in the {@code .cpp} source file
 * to be discoverable by the registry and callable through this interface.
 *
 * <p>Example usage:
 * <pre>{@code
 * @Autowired
 * CppEngine engine;
 *
 * // Direct invocation by function name
 * float result = (float) engine.execute("calculate_cosine_similarity", arrayA, arrayB, length);
 *
 * // Void function with object argument
 * engine.execute("process_particle", particle);
 * }</pre>
 *
 * @see DefaultCppEngine
 * @see org.example.client.MiteClient
 */
public interface CppEngine {

    /**
     * Resolves and invokes a native C++ function by name with the given arguments.
     *
     * <p>The function is located in the {@link org.example.scanner.FunctionRegistry}
     * by matching {@code functionName} against registered {@code // @mite}-marked symbols.
     * Arguments are marshalled to native memory, the function is called via Panama,
     * and any modifications made by C++ to pointer arguments are reflected back
     * to the original Java objects before this method returns.
     *
     * <p>Supported argument types:
     * <ul>
     *   <li>Java primitives and their boxed equivalents ({@code int}, {@code float}, etc.)</li>
     *   <li>Primitive arrays ({@code float[]}, {@code int[]}, {@code double[]}, etc.) -
     *       copied to off-heap before the call, copied back after</li>
     *   <li>{@link org.example.memory.MiteArray} - passed as a direct pointer with no copy</li>
     *   <li>{@link org.example.annotation.MiteStruct}-annotated objects - recursively
     *       marshalled to a native struct; modifications written back after the call</li>
     *   <li>{@code String} - marshalled as a null-terminated {@code const char*}</li>
     * </ul>
     *
     * @param functionName the name of the native function to invoke, as declared
     *                     after {@code // @mite} in the {@code .cpp} source file
     * @param args         the arguments to pass to the native function; order and types
     *                     must match the C++ function signature
     * @return the return value of the native function, boxed to its Java equivalent
     * (e.g., {@code float} → {@code Float}), or {@code null} for {@code void} functions
     * @throws org.example.compiler.MiteException if the function is not found, compilation fails,
     *                                            argument marshalling fails, or the native call throws an exception
     */
    Object execute(String functionName, Object... args);
}