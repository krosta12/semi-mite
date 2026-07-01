package org.example.linker;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;


public class MiteClientProxyFactory {

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> clientInterface, Map<String, MethodHandle> handles) {
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            MethodHandle handle = handles.get(method.getName());
            if (handle == null) {
                throw new UnsupportedOperationException(
                        "No native binding for method: " + method.getName());
            }
            Object[] callArgs = args == null ? new Object[0] : args;
            try {
                return handle.invokeWithArguments(callArgs);
            } catch (Throwable t) {
                throw new IllegalStateException(
                        "Native call is failed: " + method.getName(), t);
            }
        };

        return (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                invocationHandler
        );
    }

}