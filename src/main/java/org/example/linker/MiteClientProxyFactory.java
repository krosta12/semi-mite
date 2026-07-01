package org.example.linker;

import org.example.memory.ArrayMarshaller;
import org.example.memory.MiteArray;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Supplier;

public class MiteClientProxyFactory {

    private final ArrayMarshaller arrayMarshaller = new ArrayMarshaller();

    public <T> T create(Class<T> clientInterface, Map<String, MethodHandle> handles) {
        return create(clientInterface, () -> handles);
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> clientInterface, Supplier<Map<String, MethodHandle>> handlesSupplier) {
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            MethodHandle handle = handlesSupplier.get().get(method.getName());
            if (handle == null) {
                throw new UnsupportedOperationException(
                        "No native binding for method: " + method.getName());
            }
            Object[] rawArgs = args == null ? new Object[0] : args;

            try (Arena callArena = Arena.ofConfined()) {
                Object[] callArgs = new Object[rawArgs.length];
                for (int i = 0; i < rawArgs.length; i++) {
                    callArgs[i] = toNativeArg(rawArgs[i], callArena);
                }
                try {
                    return handle.invokeWithArguments(callArgs);
                } catch (Throwable t) {
                    throw new IllegalStateException(
                            "Native call failed: " + method.getName(), t);
                }
            }
        };

        return (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                invocationHandler
        );
    }

    private Object toNativeArg(Object arg, Arena callArena) {
        if (arg instanceof MiteArray miteArray) {
            return miteArray.segment();
        }
        if (arg instanceof float[] floats) {
            return arrayMarshaller.toSegment(callArena, floats);
        }
        if (arg instanceof int[] ints) {
            return arrayMarshaller.toSegment(callArena, ints);
        }
        if (arg instanceof double[] doubles) {
            return arrayMarshaller.toSegment(callArena, doubles);
        }
        return arg;
    }

}