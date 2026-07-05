package org.example.linker;

import org.example.annotation.MiteStruct;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MiteClientProxyFactory {

    private final NativeMarshaller marshaller = new NativeMarshaller();

    public <T> T create(Class<T> clientInterface, Map<String, MethodHandle> handles) {
        return create(clientInterface, () -> handles);
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> clientInterface, Supplier<Map<String, MethodHandle>> handlesSupplier) {
        InvocationHandler handler = (proxy, method, args) -> {
            MethodHandle handle = handlesSupplier.get().get(method.getName());
            if (handle == null) {
                throw new UnsupportedOperationException("No native binding for: " + method.getName());
            }

            Object[] rawArgs = args == null ? new Object[0] : args;
            String[] cppTypes = resolveCppTypes(method, rawArgs);

            try (Arena callArena = Arena.ofConfined()) {
                List<Runnable> copyBackTasks = new ArrayList<>();
                Map<Object, MemorySegment> seen = new IdentityHashMap<>();
                Map<Long, Object> nativeToJava = new HashMap<>();

                Object[] nativeArgs = new Object[rawArgs.length];
                for (int i = 0; i < rawArgs.length; i++) {
                    nativeArgs[i] = marshaller.marshal(
                            rawArgs[i], cppTypes[i], callArena, copyBackTasks, seen, nativeToJava);
                }

                Object result;
                try {
                    result = handle.invokeWithArguments(nativeArgs);
                } catch (Throwable t) {
                    throw new IllegalStateException("Native call failed: " + method.getName(), t);
                }

                for (Runnable task : copyBackTasks) {
                    task.run();
                }

                return unmarshalReturn(result, method, nativeToJava);
            }
        };

        return (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                handler
        );
    }


    private String[] resolveCppTypes(Method method, Object[] args) {
        String[] types = new String[args.length];
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            Class<?> pt = paramTypes[i];
            if (pt == String.class) {
                types[i] = "const char*";
            } else if (pt.isArray() || java.util.Collection.class.isAssignableFrom(pt)) {
                types[i] = "ptr*";
            } else if (args[i] != null && args[i].getClass().isAnnotationPresent(MiteStruct.class)) {
                types[i] = args[i].getClass().getSimpleName() + "*";
            } else {
                types[i] = null;
            }
        }
        return types;
    }

    private Object unmarshalReturn(Object result, Method method, Map<Long, Object> nativeToJava) {
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == MemorySegment.class) {
            return result;
        }
        if (returnType == String.class && result instanceof MemorySegment seg) {
            return seg.address() == 0 ? null : marshaller.readNullTerminatedString(seg);
        }
        if (result instanceof MemorySegment seg && !returnType.isPrimitive()
                && returnType.isAnnotationPresent(MiteStruct.class)) {
            return marshaller.unmarshalCustomObject(seg, returnType, nativeToJava);
        }
        return result;
    }

}