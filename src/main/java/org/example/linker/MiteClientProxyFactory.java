package org.example.linker;


import org.example.memory.ArrayMarshaller;
import org.example.memory.DeepMarshaller;
import org.example.memory.DeepUnmarshaller;
import org.example.memory.MiteArray;
import org.example.memory.StructLayoutCache;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Supplier;

public class MiteClientProxyFactory {

    private final ArrayMarshaller arrayMarshaller = new ArrayMarshaller();
    private final DeepMarshaller deepMarshaller;
    private final DeepUnmarshaller deepUnmarshaller;

    public MiteClientProxyFactory() {
        this(4);
    }

    public MiteClientProxyFactory(int alignmentBytes) {
        StructLayoutCache layoutCache = new StructLayoutCache(alignmentBytes);
        this.deepMarshaller = new DeepMarshaller(layoutCache);
        this.deepUnmarshaller = new DeepUnmarshaller(layoutCache);
    }

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
                Object result;
                try {
                    result = handle.invokeWithArguments(callArgs);
                } catch (Throwable t) {
                    throw new IllegalStateException(
                            "Native call failed: " + method.getName(), t);
                }
                return toJavaReturn(result, method);
            }
        };

        return (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                invocationHandler
        );
    }

    private Object toJavaReturn(Object result, Method method) {
        if (!(result instanceof MemorySegment segment)) {
            return result;
        }
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == MemorySegment.class) {
            return result;
        }
        return deepUnmarshaller.unmarshal(segment, returnType);
    }

    private Object toNativeArg(Object arg, Arena callArena) {
        if (arg == null) {
            return MemorySegment.NULL;
        }
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
        if (isBoxedPrimitive(arg)) {
            return arg;
        }
        return deepMarshaller.marshal(arg, callArena);
    }

    private boolean isBoxedPrimitive(Object arg) {
        return arg instanceof Number || arg instanceof Boolean || arg instanceof Character;
    }

}