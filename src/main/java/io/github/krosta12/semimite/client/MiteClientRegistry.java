package io.github.krosta12.semimite.client;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Holds the set of interfaces discovered by {@link MiteClientScanner} that are
 * annotated with {@link MiteClient}.
 */
public class MiteClientRegistry {

    private final Set<Class<?>> clients = new LinkedHashSet<>();

    /**
     * Registers a client interface.
     *
     * @param clientInterface the {@code @MiteClient}-annotated interface to register
     */
    public void register(Class<?> clientInterface) {
        clients.add(clientInterface);
    }

    /**
     * Returns all registered client interfaces.
     *
     * @return an unmodifiable view of the registered interfaces
     */
    public Set<Class<?>> getClients() {
        return Collections.unmodifiableSet(clients);
    }

}