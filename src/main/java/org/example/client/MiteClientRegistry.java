package org.example.client;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class MiteClientRegistry {

    private final Set<Class<?>> clients = new LinkedHashSet<>();

    public void register(Class<?> clientInterface) {
        clients.add(clientInterface);
    }

    public Set<Class<?>> getClients() {
        return Collections.unmodifiableSet(clients);
    }

}