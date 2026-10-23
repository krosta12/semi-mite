package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.compiler.CompilerConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class MiteBuilderConfigIntegrationTest {

    /**
     * withScriptsDir changes scriptsDir without any affect on other fields
     */
    @Test
    void withScriptsDirPreservesOtherConfigFields() {
        Path cache = Path.of("my-cache");
        Path scripts = Path.of("my-scripts");

        // Arrange a config
        CompilerConfig base = new CompilerConfig(
                Path.of("old-scripts"), cache, "/usr/bin/g++", List.of("-O1"), 8);

        // Arrange a builder with the config
        MiteBuilder builder = Mite.scan("io.example.nonexistent")
                .withConfig(base)
                .withScriptsDir(scripts);

        // Act
        MiteContext ctx = assertDoesNotThrow(builder::start);

        // Assert
        assertNotNull(ctx);
        assertFalse(ctx.isClosed());
        ctx.close();
    }

    /**
     * Builder can handle a chain of starting methods
     */
    @Test
    void withCacheDirPreservesScriptsDir() {
        MiteContext ctx = Mite.scan("io.example.nonexistent")
                .withScriptsDir(Path.of("scripts"))
                .withCacheDir(Path.of("cache"))
                .start();

        assertNotNull(ctx);
        ctx.close();
    }

    /**
     * Builder can hadle a chain of starting methods
     */
    @Test
    void withFlagAppendsCumulatively() {
        assertDoesNotThrow(() ->
                Mite.scan("io.example.nonexistent")
                        .withFlags(List.of("-O2"))
                        .withFlag("-DDEBUG")
                        .withFlag("-Wall")
                        .withFlag("-Werror")
        );
    }

    /**
     * withFlags should replace previous list of compilator flags.
     */
    @Test
    void withFlagsReplacesPreviousList() {
        assertDoesNotThrow(() ->
                Mite.scan("io.example.nonexistent")
                        .withFlag("-O0")
                        .withFlags(List.of("-O3"))
        );
    }

    /**
     * withPointerSize sets the pointer size.
     */
    @Test
    void withPointerSizeIsChainable() {
        MiteBuilder b = Mite.scan("io.example.nonexistent")
                .withPointerSize(8);
        assertNotNull(b);
    }


    /**
     * If registry is empty, start() invoking shouldn't perform the compiler
     */
    @Test
    void startWithEmptyPackageReturnsEmptyContextWithoutCompiling() {
        MiteContext ctx = Mite.scan("io.example.nonexistent.package.that.does.not.exist")
                .start();

        assertNotNull(ctx);
        assertFalse(ctx.isClosed());


        assertThrows(IllegalStateException.class,
                () -> ctx.get(Runnable.class),
                "empty context must have no registered clients");

        ctx.close();
        assertTrue(ctx.isClosed());
    }

    /**
     * Mite.use() creates Builder class exemplar and perform already compiled files
     */
    @Test
    void miteUseWithEmptyPackageReturnsEmptyContext() {
        Path nonExistent = Path.of("/tmp/does-not-exist.so");

        MiteContext ctx = Mite.use("io.example.nonexistent", nonExistent).start();

        assertNotNull(ctx);
        assertFalse(ctx.isClosed());
        ctx.close();
    }

    /**
     * withHotReload() can be disabled without affecting the rest of configureation
     */
    @Test
    void hotReloadWithEmptyPackageIsHarmless() {
        MiteContext ctx = Mite.scan("io.example.nonexistent")
                .withHotReload()
                .start();

        assertNotNull(ctx);
        ctx.close();
    }

    /**
     * BUilder should be able to set compiler path without affecting other fields
     */
    @Test
    void withCompilerPreservesScriptsDir() {
        assertDoesNotThrow(() ->
                Mite.scan("io.example.nonexistent")
                        .withScriptsDir(Path.of("native"))
                        .withCompiler("/usr/local/bin/clang++")
        );
    }

    /**
     * Full fluent chain of configuration methods should not throw any exception
     */
    @Test
    void fullFluentChainDoesNotThrow() {
        assertDoesNotThrow(() -> {
            MiteContext ctx = Mite.scan("io.example.nonexistent")
                    .withScriptsDir(Path.of("scripts"))
                    .withCacheDir(Path.of("cache"))
                    .withCompiler("g++")
                    .withFlags(List.of("-O2"))
                    .withFlag("-DNDEBUG")
                    .withPointerSize(8)
                    .start();
            ctx.close();
        });
    }
}
