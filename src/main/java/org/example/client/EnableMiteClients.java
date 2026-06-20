package org.example.client;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables scanning and registration of {@link MiteClient} interfaces
 * as Spring-managed beans backed by native C++ implementations.
 *
 * <p>Place this annotation on a {@code @SpringBootApplication} or
 * {@code @Configuration} class to activate the semi-mite client infrastructure.
 * Internally, it imports {@link MiteClientsRegistrar}, which scans the specified
 * packages for interfaces annotated with {@code @MiteClient} and registers a
 * {@link MiteClientFactoryBean} for each one.
 *
 * <p>Each discovered interface is registered as a singleton Spring bean whose
 * method calls are transparently delegated to the native C++ engine via
 * {@link MiteClientProxy}.
 *
 * <p>Example:
 * <pre>{@code
 * @SpringBootApplication
 * @EnableMiteClients(basePackages = "com.example.clients")
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }</pre>
 *
 * <p>If {@code basePackages} is not specified, the package of the annotated
 * class is used as the scan root.
 *
 * @see MiteClient
 * @see MiteClientsRegistrar
 * @see MiteClientFactoryBean
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(MiteClientsRegistrar.class)
public @interface EnableMiteClients {
    /**
     * Base packages to scan for {@link MiteClient} interfaces.
     *
     * <p>If left empty, the package of the class annotated with
     * {@code @EnableMiteClients} is used as the scan root.
     *
     * @return array of package names to scan
     */
    String[] basePackages() default {};
}