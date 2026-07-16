package io.github.krosta12.semimite.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiteTest {

    @Test
    void scanReturnsNonNull() {
        assertNotNull(Mite.scan("io.github.krosta12.semimite"));
    }

    @Test
    void scanReturnsNewInstanceEachTime() {
        MiteBuilder a = Mite.scan("io.github.krosta12.semimite");
        MiteBuilder b = Mite.scan("io.github.krosta12.semimite");

        assertNotSame(a, b);
    }

    @Test
    void scanReturnsBuilderThatSupportsFluentChain() {
        assertDoesNotThrow(() ->
                Mite.scan("io.github.krosta12.semimite")
                        .withFlag("-O2")
                        .withHotReload()
        );
    }
}
