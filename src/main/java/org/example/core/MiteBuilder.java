package org.example.core;

import org.example.compiler.CompilerConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MiteBuilder {

    private final String basePackage;
    private CompilerConfig config = CompilerConfig.defaults();
    private boolean hotReload = false;

    MiteBuilder(String basePackage) {
        this.basePackage = basePackage;
    }

    public MiteBuilder withConfig(CompilerConfig config) {
        this.config = config;
        return this;
    }

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

    public MiteBuilder withHotReload() {
        this.hotReload = true;
        return this;
    }

    public MiteContext start() {
        MiteBootstrap bootstrap = new MiteBootstrap();
        MiteContext context = bootstrap.start(basePackage, config, hotReload);
        context.setBootstrap(bootstrap);
        return context;
    }

}