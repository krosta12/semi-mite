package org.example.client;

import java.lang.annotation.*;

/**
 * Marks a Java interface as a declarative native client backed by C++ functions.
 *
 * <p>Each method declared in the annotated interface corresponds to a C++ function
 * registered in the {@code FunctionRegistry}. When a method is called, the framework
 * resolves the matching native function by name, marshals the arguments into off-heap
 * memory, invokes the function via Project Panama's downcall linker, and unmarshals
 * the return value back to Java.
 *
 * <p>Interfaces annotated with {@code @MiteClient} are discovered automatically
 * during application startup when {@link EnableMiteClients} is present. Each interface
 * is registered as a singleton Spring bean and can be injected with {@code @Autowired}.
 *
 * <p>Example:
 * <pre>{@code
 * @MiteClient
 * public interface PhysicsClient {
 *
 *     void apply_explosion_force(MiteArray x, MiteArray y, int count,
 *                                float tx, float ty, float radius, float force);
 *
 *     float calculate_cosine_similarity(MiteArray vecA, MiteArray vecB, int length);
 * }
 * }</pre>
 *
 * <p>The corresponding C++ functions must be marked with {@code // @mite} and present
 * in the configured {@code cppScripts} directory.
 *
 * <p><b>Function resolution:</b> methods are resolved by name against all functions
 * registered from the {@code cppScripts} directory. The {@code script} attribute
 * is informational and does not restrict resolution to a specific file. Function
 * names must therefore be unique across all {@code .cpp} files in the scripts directory.
 *
 * @see EnableMiteClients
 * @see MiteClientProxy
 * @see org.example.scanner.FunctionRegistry
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MiteClient {

    /**
     * Informational path to the {@code .cpp} source file that provides
     * the native implementations for this client's methods.
     *
     * <p><b>Note:</b> this attribute is currently informational only.
     * Function resolution is performed by name against all registered
     * functions, regardless of which file declared them. This value
     * does not restrict or filter the resolution process.
     *
     * @return relative path to the associated {@code .cpp} file,
     * or an empty string if not specified
     */
    String script() default "";
}