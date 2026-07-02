package org.example.client;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MiteClientRegistryTest {

    @Test
    void registeredInterfaceIsVisible() {
        MiteClientRegistry registry = new MiteClientRegistry();
        registry.register(SampleClient.class);

        assertTrue(registry.getClients().contains(SampleClient.class));
    }

    @Test
    void getClientsIsUnmodifiable() {
        MiteClientRegistry registry = new MiteClientRegistry();
        registry.register(SampleClient.class);

        Set<Class<?>> clients = registry.getClients();
        assertThrows(UnsupportedOperationException.class, () -> clients.add(NotAClient.class));
    }

    @Test
    void duplicateRegistrationOnlyAppearsOnce() {
        MiteClientRegistry registry = new MiteClientRegistry();
        registry.register(SampleClient.class);
        registry.register(SampleClient.class);

        assertEquals(1, registry.getClients().size());
    }

    @Test
    void emptyByDefault() {
        MiteClientRegistry registry = new MiteClientRegistry();

        assertTrue(registry.getClients().isEmpty());
    }

    @Test
    void preservesInsertionOrder() {
        MiteClientRegistry registry = new MiteClientRegistry();
        registry.register(SampleClient.class);
        registry.register(NotAClient.class);

        Iterator<Class<?>> it = registry.getClients().iterator();
        assertEquals(SampleClient.class, it.next());
        assertEquals(NotAClient.class, it.next());
    }
}
