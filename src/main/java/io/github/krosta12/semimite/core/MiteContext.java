package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.linker.ManagedLibrary;
import io.github.krosta12.semimite.memory.MiteArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime container holding created client proxies, tracked {@link MiteArray}
 * instances, and managed native libraries. Closing the context releases all
 * associated native resources.
 */
public class MiteContext implements AutoCloseable {

    private final Map<Class<?>, Object> instances = new HashMap<>();
    private final List<ManagedLibrary> libraries = new CopyOnWriteArrayList<>();
    private final List<MiteArray<?>> arrays = new ArrayList<>();
    private MiteBootstrap bootstrap;
    private volatile boolean closed = false;

    /**
     * Associates this context with the bootstrap that created it, so it can be
     * closed together with the context.
     *
     * @param bootstrap the owning bootstrap
     */
    void setBootstrap(MiteBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * Registers a client proxy instance under its interface.
     *
     * @param clientInterface the client interface
     * @param instance        the proxy instance implementing it
     */
    public void put(Class<?> clientInterface, Object instance) {
        instances.put(clientInterface, instance);
    }

    /**
     * Returns the registered client proxy for the given interface.
     *
     * @param clientInterface the client interface
     * @param <T>             the client type
     * @return the registered proxy instance
     * @throws IllegalStateException if no proxy is registered for {@code clientInterface}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clientInterface) {
        Object instance = instances.get(clientInterface);
        if (instance == null) {
            throw new IllegalStateException("No client registered for: " + clientInterface.getName());
        }
        return (T) instance;
    }

    /**
     * Registers a {@link MiteArray} so it is automatically closed when this
     * context is closed.
     *
     * @param array the array to track
     * @param <T>   the array's element type
     * @return the same array instance, for chaining
     */
    public <T> MiteArray<T> track(MiteArray<T> array) {
        arrays.add(array);
        return array;
    }

    /**
     * Registers a managed native library so it is retired when this context is closed.
     *
     * @param library the managed library
     */
    void addLibrary(ManagedLibrary library) {
        libraries.add(library);
    }

    /**
     * Forgets a library that has already been retired.
     *
     * @param library the retired library to forget
     */
    void removeLibrary(ManagedLibrary library) {
        libraries.remove(library);
    }

    /**
     * Closes this context: closes the owning bootstrap (if any), all tracked
     * {@link MiteArray} instances, and retires all managed native libraries.
     * Safe to call multiple times.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (bootstrap != null) {
            try {
                bootstrap.close();
            } catch (Exception ignored) {
            }
        }
        for (MiteArray<?> array : arrays) {
            try {
                array.close();
            } catch (Exception ignored) {
            }
        }
        for (ManagedLibrary lib : libraries) {
            lib.retire();
        }
        arrays.clear();
        libraries.clear();
    }

    /**
     * Returns whether this context has already been closed.
     *
     * @return {@code true} if {@link #close()} has been called
     */
    public boolean isClosed() {
        return closed;
    }

}