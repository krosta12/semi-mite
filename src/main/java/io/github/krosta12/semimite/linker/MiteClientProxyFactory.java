package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.annotation.MiteStruct;

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

/**
 * Creates dynamic proxies ({@link Proxy}) for {@code @MiteClient} interfaces.
 * Each proxy method invocation marshals its arguments into native memory,
 * invokes the corresponding native function via a {@link MethodHandle}, and
 * unmarshals the result back into a Java object.
 */
public class MiteClientProxyFactory {

    private final NativeMarshaller marshaller;

    /**
     * Creates a factory using the default marshaller (8-byte pointer size).
     */
    public MiteClientProxyFactory() {
        this.marshaller = new NativeMarshaller();
    }

    /**
     * Creates a factory using a marshaller configured for the given pointer size.
     *
     * @param pointerSize the native pointer size in bytes (4 or 8)
     */
    public MiteClientProxyFactory(int pointerSize) {
        this.marshaller = new NativeMarshaller(pointerSize);
    }

    /**
     * Creates a proxy for the given client interface, bound to a fixed library.
     *
     * @param clientInterface the {@code @MiteClient} interface to implement
     * @param library         the managed native library to bind calls to
     * @param <T>             the client interface type
     * @return a proxy instance implementing {@code clientInterface}
     */
    public <T> T create(Class<T> clientInterface, ManagedLibrary library) {
        return create(clientInterface, () -> library);
    }

    /**
     * Creates a proxy for the given client interface that resolves the target
     * library on every call via {@code librarySupplier} (required to support
     * hot reload, where the underlying library instance may change over time).
     * <p>
     * Each invocation: acquires the current library, resolves C++ argument
     * type hints, marshals arguments into native memory, invokes the native
     * function, runs any copy-back tasks, unmarshals the return value, and
     * finally releases the library.
     *
     * @param clientInterface the {@code @MiteClient} interface to implement
     * @param librarySupplier supplies the current managed library on each call
     * @param <T>             the client interface type
     * @return a proxy instance implementing {@code clientInterface}
     */
    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> clientInterface, Supplier<ManagedLibrary> librarySupplier) {
        InvocationHandler handler = (proxy, method, args) -> {
            ManagedLibrary library = librarySupplier.get();
            if (!library.acquire()) {
                throw new IllegalStateException(
                        "Native library is being reloaded, retry call: " + method.getName());
            }
            try {
                MethodHandle handle = library.handles().get(method.getName());
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
            } finally {
                library.release();
            }
        };

        return (T) Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                handler
        );
    }


    /**
     * Derives a C++ type hint for each argument based on the Java method's
     * parameter types (used to decide how to marshal each argument).
     *
     * @param method the invoked interface method
     * @param args   the actual call arguments
     * @return an array of C++ type hints, one per argument (may contain {@code null})
     */
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

    /**
     * Converts a raw native call result into the Java type declared by the
     * interface method's return type (string, {@code @MiteStruct} object, or
     * passed through unchanged).
     *
     * @param result       the raw value returned by the native call
     * @param method       the invoked interface method
     * @param nativeToJava cache of already-unmarshalled native addresses to Java objects
     * @return the converted return value
     */
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