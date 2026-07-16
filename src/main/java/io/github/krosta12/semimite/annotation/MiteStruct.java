package io.github.krosta12.semimite.annotation;

import io.github.krosta12.semimite.linker.NativeMarshaller;

import java.lang.annotation.*;


/**
 * Marks Java class as a native-compatible structure that can be marshalled
 * into off-heap memory and passed into C++ functions using the Java FFM API
 * (Project Panama).
 * <p>
 * Classes annotated with {@code @MiteStruct} are processed by
 * {@link NativeMarshaller}, which computes a field layout
 * (offsets, size, alignment) matching a C/C++ struct and copies field values
 * to and from native memory.
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiteStruct {
}