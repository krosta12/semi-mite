package org.example.client;

import org.example.engine.CppEngine;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring {@link FactoryBean} that creates dynamic proxy instances for
 * {@link MiteClient}-annotated interfaces.
 *
 * <p>One {@code MiteClientFactoryBean} is registered per discovered {@code @MiteClient}
 * interface by {@link MiteClientsRegistrar} during application context initialization.
 * When Spring requests the bean, {@link #getObject()} resolves the {@link CppEngine}
 * from the application context and delegates proxy creation to {@link MiteClientProxy}.
 *
 * <p>The resulting proxy is registered as a singleton bean of the interface type,
 * making it directly injectable via {@code @Autowired} throughout the application.
 *
 * <p>This class is not intended to be used directly. Use {@link EnableMiteClients}
 * and {@link MiteClient} to activate client registration.
 *
 * @see MiteClientsRegistrar
 * @see MiteClientProxy
 * @see CppEngine
 */
public class MiteClientFactoryBean implements FactoryBean<Object>, ApplicationContextAware {

    private Class<?> interfaceClass;
    private String script;
    private ApplicationContext applicationContext;

    /**
     * No-arg constructor required by Spring's bean definition infrastructure.
     * Properties are injected via {@link #setInterfaceClass} and {@link #setScript}
     * after construction.
     */
    public MiteClientFactoryBean() {
    }

    /**
     * Constructs a factory bean for the given interface and script path.
     *
     * @param interfaceClass the {@code @MiteClient}-annotated interface
     *                       for which a proxy will be created
     * @param script         informational path to the associated {@code .cpp} file;
     *                       passed through to the proxy for diagnostic purposes
     */
    public MiteClientFactoryBean(Class<?> interfaceClass, String script) {
        this.interfaceClass = interfaceClass;
        this.script = script;
    }

    /**
     * Creates and returns a dynamic proxy that implements {@code interfaceClass}.
     *
     * <p>The proxy delegates all method calls to the {@link CppEngine} bean
     * resolved from the application context. Each method invocation results in
     * a native C++ function call matched by method name.
     *
     * @return a proxy instance implementing {@code interfaceClass}
     */
    @Override
    public Object getObject() {
        CppEngine engine = applicationContext.getBean(CppEngine.class);
        return MiteClientProxy.create(interfaceClass, engine, script);
    }

    /**
     * Returns the type of object produced by this factory bean.
     *
     * @return the {@code @MiteClient} interface class
     */
    @Override
    public Class<?> getObjectType() {
        return interfaceClass;
    }

    /**
     * Injects the Spring application context, used to resolve the {@link CppEngine} bean.
     *
     * @param applicationContext the current Spring application context
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Sets the interface class this factory bean will produce a proxy for.
     *
     * @param interfaceClass the {@code @MiteClient}-annotated interface
     */
    public void setInterfaceClass(Class<?> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    /**
     * Sets the informational script path associated with this client.
     *
     * @param script relative path to the {@code .cpp} file, or empty string if unspecified
     */
    public void setScript(String script) {
        this.script = script;
    }
}