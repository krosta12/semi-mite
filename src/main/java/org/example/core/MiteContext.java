package org.example.core;

import org.example.linker.ManagedLibrary;
import org.example.memory.MiteArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class MiteContext implements AutoCloseable {

    private final Map<Class<?>, Object> instances = new HashMap<>();
    private final List<ManagedLibrary> libraries = new CopyOnWriteArrayList<>();
    private final List<MiteArray<?>> arrays = new ArrayList<>();
    private MiteBootstrap bootstrap;
    private volatile boolean closed = false;

    void setBootstrap(MiteBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void put(Class<?> clientInterface, Object instance) {
        instances.put(clientInterface, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clientInterface) {
        Object instance = instances.get(clientInterface);
        if (instance == null) {
            throw new IllegalStateException("No client registered for: " + clientInterface.getName());
        }
        return (T) instance;
    }

    public <T> MiteArray<T> track(MiteArray<T> array) {
        arrays.add(array);
        return array;
    }

    void addLibrary(ManagedLibrary library) {
        libraries.add(library);
    }

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

    public boolean isClosed() {
        return closed;
    }

}