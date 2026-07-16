package io.github.krosta12.semimite.client;

import io.github.krosta12.semimite.linker.MiteClientProxyFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a Java interface as a native library client.
 * <p>
 * Methods declared on interfaces annotated with {@code @MiteClient} are bound
 * at runtime to functions exported by the compiled native (C++) library, via
 * a dynamic proxy created by {@link MiteClientProxyFactory}.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MiteClient {

    /**
     * Optional script identifier/path associated with this client
     *
     * @return the script name, or an empty string if not set
     */
    String script() default "";

}