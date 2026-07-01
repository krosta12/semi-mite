package org.example.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiteClientScannerTest {

    private final MiteClientScanner scanner = new MiteClientScanner();

    @Test
    void findsAnnotatedInterface() {
        MiteClientRegistry registry = scanner.scan("org.example.client");

        assertTrue(registry.getClients().contains(SampleClient.class));
    }

    @Test
    void ignoresUnannotatedInterface() {
        MiteClientRegistry registry = scanner.scan("org.example.client");

        assertFalse(registry.getClients().contains(NotAClient.class));
    }

    @Test
    void returnsEmptyForPackageWithoutClients() {
        MiteClientRegistry registry = scanner.scan("org.example.parser");

        assertTrue(registry.getClients().isEmpty());
    }

}