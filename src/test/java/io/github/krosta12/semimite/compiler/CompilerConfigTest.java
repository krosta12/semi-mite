package io.github.krosta12.semimite.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompilerConfigTest {

    @Test
    void defaultsHaveExpectedValues() {
        CompilerConfig config = CompilerConfig.defaults();

        assertEquals(Path.of("cppScripts"), config.scriptsDir());
        assertEquals(Path.of(".mite-cache"), config.cacheDir());
        assertNull(config.compilerPath());
        assertEquals(List.of("-O2"), config.compilerFlags());
        assertEquals(8, config.alignmentBytes());
    }

    @Test
    void constructorStoresAllFields() {
        Path scripts = Path.of("scripts");
        Path cache = Path.of("cache");
        List<String> flags = List.of("-O3", "-march=native");

        CompilerConfig config = new CompilerConfig(scripts, cache, "/usr/bin/g++", flags, 8);

        assertEquals(scripts, config.scriptsDir());
        assertEquals(cache, config.cacheDir());
        assertEquals("/usr/bin/g++", config.compilerPath());
        assertEquals(flags, config.compilerFlags());
        assertEquals(8, config.alignmentBytes());
    }

    @Test
    void equalConfigsAreEqual() {
        CompilerConfig a = CompilerConfig.defaults();
        CompilerConfig b = CompilerConfig.defaults();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
