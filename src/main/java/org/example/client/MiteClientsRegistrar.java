package org.example.client;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.*;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Map;
import java.util.Set;

/**
 * Spring {@link ImportBeanDefinitionRegistrar} that scans the classpath for
 * {@link MiteClient}-annotated interfaces and registers a {@link MiteClientFactoryBean}
 * for each one.
 *
 * <p>This registrar is activated by {@link EnableMiteClients} via {@code @Import}.
 * It runs during the Spring context refresh phase, before any beans are instantiated,
 * and populates the bean registry with factory bean definitions that will produce
 * native-backed proxy instances on first access.
 *
 * <p>Scanning behaviour:
 * <ul>
 *   <li>If {@link EnableMiteClients#basePackages()} is specified, those packages are scanned.</li>
 *   <li>Otherwise, the package of the class annotated with {@code @EnableMiteClients}
 *       is used as the scan root.</li>
 *   <li>Only interfaces (not concrete classes) annotated with {@code @MiteClient}
 *       are considered candidates.</li>
 * </ul>
 *
 * <p>For each discovered interface, a {@link MiteClientFactoryBean} bean definition
 * is registered under the decapitalized simple name of the interface
 * (e.g., {@code PhysicsClient} → bean name {@code physicsClient}).
 *
 * <p>This class is not intended to be used directly.
 *
 * @see EnableMiteClients
 * @see MiteClient
 * @see MiteClientFactoryBean
 */
public class MiteClientsRegistrar implements ImportBeanDefinitionRegistrar {

    /**
     * Scans for {@link MiteClient} interfaces and registers a
     * {@link MiteClientFactoryBean} bean definition for each one.
     *
     * @param metadata the annotation metadata of the class annotated with
     *                 {@link EnableMiteClients}; used to determine the scan root
     *                 and read {@code basePackages}
     * @param registry the bean definition registry to register factory beans into
     * @throws RuntimeException if a discovered candidate class cannot be loaded
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(
                EnableMiteClients.class.getName()
        );

        String[] basePackages;
        if (attrs != null && ((String[]) attrs.get("basePackages")).length > 0) {
            basePackages = (String[]) attrs.get("basePackages");
        } else {
            basePackages = new String[]{
                    metadata.getClassName().substring(0, metadata.getClassName().lastIndexOf('.'))
            };
        }

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(MiteClient.class));

        for (String basePackage : basePackages) {
            Set<org.springframework.beans.factory.config.BeanDefinition> candidates =
                    scanner.findCandidateComponents(basePackage);

            for (var candidate : candidates) {
                try {
                    Class<?> interfaceClass = Class.forName(candidate.getBeanClassName());
                    MiteClient annotation = interfaceClass.getAnnotation(MiteClient.class);

                    BeanDefinitionBuilder builder = BeanDefinitionBuilder
                            .genericBeanDefinition(MiteClientFactoryBean.class);
                    builder.addPropertyValue("interfaceClass", interfaceClass);
                    builder.addPropertyValue("script", annotation.script());

                    AbstractBeanDefinition beanDef = builder.getBeanDefinition();
                    beanDef.setAttribute("factoryBeanObjectType", interfaceClass);

                    String beanName = decapitalize(interfaceClass.getSimpleName());
                    registry.registerBeanDefinition(beanName, beanDef);

                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Failed to load MiteClient: " + candidate.getBeanClassName(), e);
                }
            }
        }
    }

    /**
     * Converts the first character of the given string to lowercase,
     * following standard Java bean naming conventions.
     *
     * <p>Used to derive the Spring bean name from the interface simple name
     * (e.g., {@code PhysicsClient} → {@code physicsClient}).
     *
     * @param name the string to decapitalize; may be {@code null} or empty
     * @return the decapitalized string, or the original value if {@code null} or empty
     */
    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}