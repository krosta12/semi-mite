package io.github.krosta12.semimite.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a {@link MiteStruct} field as stored by value inside its parent
 * (a plain {@code T} member in C++), instead of behind a pointer.
 * <p>
 * On an array field the element count must be given:
 * {@code @MiteInline(length = 4) double[] values} matches {@code double values[4]}.
 * </p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MiteInline {

    /**
     * Number of elements of an inline array field. Ignored for non-array fields.
     *
     * @return the declared element count, or {@code 0}
     */
    int length() default 0;
}
