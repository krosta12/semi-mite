package org.example.client;

import org.example.engine.CppEngine;

import java.lang.reflect.*;

/**
 * Dynamic proxy {@link InvocationHandler} that routes interface method calls
 * to native C++ functions via {@link CppEngine}.
 *
 * <p>An instance of this class is created per {@link MiteClient} interface by
 * {@link MiteClientFactoryBean}. When a method is invoked on the proxy:
 * <ol>
 *   <li>Methods declared on {@link Object} ({@code toString}, {@code hashCode},
 *       {@code equals}) are handled locally without touching the native layer.</li>
 *   <li>All other methods are forwarded to {@link CppEngine#execute(String, Object...)}
 *       using the method's simple name as the native function identifier.</li>
 * </ol>
 *
 * <p>The proxy does not perform argument validation or type coercion - that
 * responsibility belongs to the marshalling layer inside {@code DefaultCppEngine}.
 *
 * <p>This class is not intended to be used directly. Proxies are created and
 * managed by {@link MiteClientFactoryBean}.
 *
 * @see MiteClient
 * @see MiteClientFactoryBean
 * @see CppEngine
 */
public record MiteClientProxy(CppEngine engine, String script) implements InvocationHandler {

    /**
     * Constructs a proxy handler backed by the given engine.
     *
     * @param engine the {@link CppEngine} instance used to execute native functions
     * @param script informational script path from the {@link MiteClient} annotation;
     *               used only for {@code toString()} output
     */
    public MiteClientProxy {
    }

    /**
     * Intercepts method calls on the proxy instance.
     *
     * <p>Methods declared on {@link Object} are handled locally.
     * All other methods are dispatched to the native engine by method name.
     *
     * @param proxy  the proxy instance the method was called on
     * @param method the interface method being invoked
     * @param args   the arguments passed to the method, or {@code null} if none
     * @return the result returned by the native function, or {@code null} for {@code void} methods
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(method, args);
        }

        Object[] safeArgs = args != null ? args : new Object[0];
        return engine.execute(method.getName(), safeArgs);
    }

    /**
     * Handles {@link Object} methods locally without delegating to the native layer.
     *
     * <p>Supported methods:
     * <ul>
     *   <li>{@code toString()} - returns {@code "MiteClient[script=<path>]"}</li>
     *   <li>{@code hashCode()} - returns the identity hash code of this handler</li>
     *   <li>{@code equals(Object)} - returns {@code true} only for reference equality</li>
     * </ul>
     *
     * @param method the {@link Object} method being invoked
     * @param args   method arguments
     * @return the result appropriate for the given {@link Object} method
     */
    private Object handleObjectMethod(Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "MiteClient[script=" + script + "]";
            case "hashCode" -> System.identityHashCode(this);
            case "equals" -> this == args[0];
            default -> null;
        };
    }

    /**
     * Creates a JDK dynamic proxy implementing the given interface,
     * backed by a new {@code MiteClientProxy} handler.
     *
     * @param <T>            the interface type
     * @param interfaceClass the {@link MiteClient}-annotated interface to proxy
     * @param engine         the engine used to dispatch native function calls
     * @param script         informational script path from the annotation
     * @return a proxy instance that implements {@code interfaceClass}
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> interfaceClass, CppEngine engine, String script) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class[]{interfaceClass},
                new MiteClientProxy(engine, script)
        );
    }
}