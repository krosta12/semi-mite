package org.example.compiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NativeCompiler {

    private final CompilerResolver resolver = new CompilerResolver();

    public Path compile(List<Path> sources, CompilerConfig config, String libraryName) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No source files to compile");
        }
        try {
            Files.createDirectories(config.cacheDir());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String compiler = resolver.resolve(config);
        Path output = config.cacheDir().resolve(libraryName + libraryExtension());

        List<String> command = new ArrayList<>();
        command.add(compiler);
        command.add("-shared");
        command.add("-fPIC");
        command.addAll(config.compilerFlags());
        for (Path source : sources) {
            command.add(source.toString());
        }
        command.add("-o");
        command.add(output.toString());

        run(command);
        return output;
    }

    private void run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String log = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Native compilation failed (exit " + exitCode + "):\n" + log);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Compilation interrupted", e);
        }
    }

    private String libraryExtension() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return ".dll";
        }
        if (os.contains("mac")) {
            return ".dylib";
        }
        return ".so";
    }

}