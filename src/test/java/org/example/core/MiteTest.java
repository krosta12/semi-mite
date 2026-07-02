package org.example.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiteTest {

    @Test
    void scanReturnsNonNull() {
        assertNotNull(Mite.scan("org.example"));
    }

    @Test
    void scanReturnsNewInstanceEachTime() {
        MiteBuilder a = Mite.scan("org.example");
        MiteBuilder b = Mite.scan("org.example");

        assertNotSame(a, b);
    }

    @Test
    void scanReturnsBuilderThatSupportsFluentChain() {
        assertDoesNotThrow(() ->
                Mite.scan("org.example")
                        .withFlag("-O2")
                        .withHotReload()
        );
    }
}
