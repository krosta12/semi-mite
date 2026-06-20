package org.example.compiler;

/**
 * Unchecked exception thrown by the semi-mite framework when a non-recoverable
 * error occurs during compilation, library loading, native function invocation,
 * or object marshalling.
 *
 * <p>This exception wraps lower-level checked exceptions ({@link java.io.IOException},
 * {@link InterruptedException}, reflection errors) and surfaces them as a single
 * consistent exception type across the framework, making it easier for callers
 * to handle semi-mite failures without catching unrelated checked exceptions.
 *
 * <p>Common causes include:
 * <ul>
 *   <li>C++ compiler not found or compilation failed (exit code != 0)</li>
 *   <li>Cache directory could not be created</li>
 *   <li>Native shared library could not be loaded by Panama</li>
 *   <li>Requested native function not found in the {@link org.example.scanner.FunctionRegistry}</li>
 *   <li>Marshalling failure due to unsupported field type or memory layout mismatch</li>
 * </ul>
 *
 * @see CppCompiler
 * @see org.example.engine.DefaultCppEngine
 */
public class MiteException extends RuntimeException {

    /**
     * Constructs a {@code MiteException} with the given detail message.
     *
     * @param message human-readable description of the error
     */
    public MiteException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code MiteException} with the given detail message and cause.
     *
     * @param message human-readable description of the error
     * @param cause   the underlying exception that triggered this error;
     *                preserved for stack trace and root cause analysis
     */
    public MiteException(String message, Throwable cause) {
        super(message, cause);
    }
}