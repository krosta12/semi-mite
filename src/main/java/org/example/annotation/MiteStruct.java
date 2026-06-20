package org.example.annotation;

import java.lang.annotation.*;

/**
 * Marks a Java class as a native-compatible struct that can be marshalled
 * into off-heap memory and passed directly to C++ functions via Project Panama.
 *
 * <p>Classes annotated with {@code @MiteStruct} are processed by the deep
 * marshalling layer in {@code DefaultCppEngine}. Each instance is recursively
 * serialized into a contiguous native memory segment whose layout mirrors
 * the corresponding C++ struct definition.
 *
 * <p>Supported field types:
 * <ul>
 *   <li>Primitives: {@code int}, {@code long}, {@code float}, {@code double},
 *       {@code boolean}, {@code byte}</li>
 *   <li>{@code String} - marshalled as a null-terminated native string pointer</li>
 *   <li>Nested {@code @MiteStruct} objects - marshalled recursively</li>
 *   <li>{@code List<T>} and arrays of {@code @MiteStruct} types</li>
 * </ul>
 *
 * <p>Cyclic references between {@code @MiteStruct} objects are handled
 * automatically via identity tracking. The same Java object will always
 * map to the same native address within a single marshalling call,
 * preventing infinite recursion and preserving reference equality on
 * the C++ side.
 *
 * <p>Example:
 * <pre>{@code
 * @MiteStruct
 * public class Particle {
 *     private int id;
 *     private float x;
 *     private float y;
 *     private float speed;
 * }
 * }</pre>
 *
 * @see org.example.engine.DefaultCppEngine
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiteStruct {
}