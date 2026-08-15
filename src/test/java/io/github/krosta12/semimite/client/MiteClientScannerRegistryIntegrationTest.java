package io.github.krosta12.semimite.client;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;


class MiteClientScannerRegistryIntegrationTest {

    private final MiteClientScanner scanner = new MiteClientScanner();


    /**
     * Should find only interfaces annotated with @MiteClient in the package.
     */
    @Test
    void scansPackageAndFindsOnlyAnnotatedInterfaces() {
        MiteClientRegistry registry = scanner.scan("io.github.krosta12.semimite.client");

        Set<Class<?>> clients = registry.getClients();

        assertTrue(clients.contains(SampleClient.class),
                "SampleClient annotated with @MiteClient must be discovered");
        assertFalse(clients.contains(NotAClient.class),
                "NotAClient without @MiteClient must NOT be discovered");
    }

    /**
     * None interface classes never should be registred in the registry.
     */
    @Test
    void doesNotRegisterConcreteClasses() {
        MiteClientRegistry registry = scanner.scan("io.github.krosta12.semimite.client");

        // MiteClientScanner should never register concrete classes, only interfaces
        assertFalse(registry.getClients().contains(MiteClientScanner.class));
    }

    /**
     * Multy scans of the same folders system shold return the same set
     */
    @Test
    void repeatedScanReturnsEquivalentRegistry() {
        MiteClientRegistry r1 = scanner.scan("io.github.krosta12.semimite.client");
        MiteClientRegistry r2 = scanner.scan("io.github.krosta12.semimite.client");

        assertEquals(r1.getClients(), r2.getClients(),
                "Two scans of the same package must yield identical client sets");
    }

    /**
     * If the package doesn't exist, registry should be empty
     */
    @Test
    void nonExistentPackageReturnsEmptyRegistry() {
        MiteClientRegistry registry = scanner.scan("com.definitely.does.not.exist");

        assertTrue(registry.getClients().isEmpty(),
                "Non-existent package must produce an empty registry");
    }

    /**
     * Should return an empty registry when scans a package without any @MiteClient annotated interfaces
     */
    @Test
    void packageWithoutMiteClientsReturnsEmptyRegistry() {
        MiteClientRegistry registry = scanner.scan("io.github.krosta12.semimite.parser");
        assertTrue(registry.getClients().isEmpty());
    }

}
