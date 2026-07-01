package org.example.core;

import java.util.HashMap;
import java.util.Map;

public class MiteContext {

    private final Map<Class<?>, Object> instances = new HashMap<>();

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

}