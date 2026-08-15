package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.compiler.CompilerConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for configuring and starting a {@link MiteContext}
 * (compiler configuration, hot reload, etc.).
 */
public class MiteBuilder {

    private final String basePackage;
    private CompilerConfig config = CompilerConfig.defaults();
    private boolean hotReload = false;

    private Path prebuiltLibraryPath = null;

    /**
     * Creates a builder for the given base package.
     *
     * @param basePackage the package to scan for {@code @MiteClient} interfaces
     */
    MiteBuilder(String basePackage) {
        this.basePackage = basePackage;
    }

    /**
     * Replaces the entire compiler configuration.
     *
     * @param config the configuration to use
     * @return this builder, for chaining
     */
    public MiteBuilder withConfig(CompilerConfig config) {
        this.config = config;
        return this;
    }

    /**
     * Sets the directory containing native {@code .cpp} sources.
     *
     * @param scriptsDir the scripts directory
     * @return this builder, for chaining
     */
    public MiteBuilder withScriptsDir(Path scriptsDir) {
        this.config = new CompilerConfig(
                scriptsDir,
                config.cacheDir(),
                config.compilerPath(),
                config.compilerFlags(),
                config.alignmentBytes()
        );
        return this;
    }

    /**
     * Sets the directory used to cache compiled native libraries.
     *
     * @param cacheDir the cache directory
     * @return this builder, for chaining
     */
    public MiteBuilder withCacheDir(Path cacheDir) {
        this.config = new CompilerConfig(
                config.scriptsDir(),
                cacheDir,
                config.compilerPath(),
                config.compilerFlags(),
                config.alignmentBytes()
        );
        return this;
    }

    /**
     * Sets an explicit path to the native compiler executable
     *
     * @param compilerPath the compiler path or name
     * @return this builder, for chaining
     */
    public MiteBuilder withCompiler(String compilerPath) {
        this.config = new CompilerConfig(
                config.scriptsDir(),
                config.cacheDir(),
                compilerPath,
                config.compilerFlags(),
                config.alignmentBytes()
        );
        return this;
    }

    /**
     * Replaces the entire list of compiler flags
     *
     * @param flags the compiler flags to use
     * @return this builder, for chaining
     */
    public MiteBuilder withFlags(List<String> flags) {
        this.config = new CompilerConfig(
                config.scriptsDir(),
                config.cacheDir(),
                config.compilerPath(),
                new ArrayList<>(flags),
                config.alignmentBytes()
        );
        return this;
    }

    /**
     * Adds a single compiler flag to the existing list of flags.
     *
     * @param flag the flag to add
     * @return this builder, for chaining
     */
    public MiteBuilder withFlag(String flag) {
        List<String> updated = new ArrayList<>(config.compilerFlags());
        updated.add(flag);
        this.config = new CompilerConfig(
                config.scriptsDir(),
                config.cacheDir(),
                config.compilerPath(),
                updated,
                config.alignmentBytes()
        );
        return this;
    }

    /**
     * Sets the native pointer/alignment size in bytes.
     *
     * @param alignmentBytes the alignment size (typically 4 or 8)
     * @return this builder, for chaining
     */
    public MiteBuilder withAlignment(int alignmentBytes) {
        this.config = new CompilerConfig(
                config.scriptsDir(),
                config.cacheDir(),
                config.compilerPath(),
                config.compilerFlags(),
                alignmentBytes
        );
        return this;
    }

    /**
     * Enables hot reload: native sources are watched for changes and the
     * library is recompiled and relinked automatically.
     *
     * @return this builder, for chaining
     */
    public MiteBuilder withHotReload() {
        this.hotReload = true;
        return this;
    }

    /**
     * Builds and starts the Mite runtime with the configured options.
     *
     * @return a ready-to-use {@link MiteContext}
     */
    public MiteContext start() {
        MiteBootstrap bootstrap = new MiteBootstrap();
        MiteContext context;
        if (prebuiltLibraryPath != null) {
            context = bootstrap.startWithLibrary(
                    basePackage,
                    prebuiltLibraryPath,
                    config.alignmentBytes()
            );
        } else {
            context = bootstrap.start(basePackage, config, hotReload);
        }
        context.setBootstrap(bootstrap);
        return context;
    }

    public MiteBuilder withPrebuiltLibrary(Path libraryPath) {
        this.prebuiltLibraryPath = libraryPath;
        return this;
    }

}