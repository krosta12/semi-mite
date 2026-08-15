package io.github.krosta12.semimite.client;

import org.junit.jupiter.api.Test;

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
}
