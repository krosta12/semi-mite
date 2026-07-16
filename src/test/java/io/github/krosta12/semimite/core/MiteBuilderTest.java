package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.compiler.CompilerConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MiteBuilderTest {

    @Test
    void withConfigReturnsSelfAndAllowsFurtherChaining() {
        MiteBuilder builder = Mite.scan("io.github.krosta12.semimite");
        CompilerConfig custom = new CompilerConfig(
                Path.of("s"), Path.of("c"), "/usr/bin/g++", List.of("-O3"), 8);

        MiteBuilder result = builder.withConfig(custom);

        assertSame(builder, result);

        assertDoesNotThrow(() -> builder.withFlag("-DDEBUG"));
    }

    @Test
    void withConfigReturnsSelf() {
        MiteBuilder builder = Mite.scan("pkg");
        CompilerConfig config = CompilerConfig.defaults();

        assertSame(builder, builder.withConfig(config));
    }

    @Test
    void withScriptsDirReturnsSelf() {
        MiteBuilder builder = Mite.scan("pkg");
        assertSame(builder, builder.withScriptsDir(Path.of("scripts")));
    }

    @Test
    void withCacheDirReturnsSelf() {
        MiteBuilder builder = Mite.scan("pkg");
        assertSame(builder, builder.withCacheDir(Path.of("cache")));
    }

    @Test
    void withCompilerReturnsSelf() {
        MiteBuilder builder = Mite.scan("pkg");
        assertSame(builder, builder.withCompiler("/usr/bin/g++"));
    }

    @Test
    void withFlagsReturnsSelf() {
        MiteBuilder builder = Mite.scan("pkg");
        assertSame(builder, builder.withFlags(List.of("-O2")));
    }

    @Test
    void withFlagReturnsSelf() {
        MiteBuilder builder = Mite.scan("pkg");
        assertSame(builder, builder.withFlag("-O3"));
    }

    @Test
    void withHotReloadReturnsSelf() {
        MiteBuilder builder = Mite.scan("pkg");
        assertSame(builder, builder.withHotReload());
    }

    @Test
    void withFlagAppendsToPreviousFlags() {
        MiteBuilder builder = Mite.scan("pkg")
                .withFlags(List.of("-O2"))
                .withFlag("-DDEBUG")
                .withFlag("-Wall");


        assertNotNull(builder);
    }

    @Test
    void withFlagsReplacesPreviousFlags() {
        MiteBuilder builder = Mite.scan("pkg")
                .withFlag("-O2")
                .withFlags(List.of("-O3"));

        assertNotNull(builder);
    }

    @Test
    void withScriptsDirPreservesOtherConfigFields() {
        Path cacheDir = Path.of("my-cache");
        MiteBuilder builder = Mite.scan("pkg")
                .withCacheDir(cacheDir)
                .withScriptsDir(Path.of("my-scripts"));

        assertNotNull(builder);
    }
}
