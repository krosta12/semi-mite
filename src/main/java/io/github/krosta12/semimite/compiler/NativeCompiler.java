package io.github.krosta12.semimite.compiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Compiles C++ source files into a native shared library
 * ({@code .so} / {@code .dll} / {@code .dylib}) using the system compiler.
 */
public class NativeCompiler {

    private final CompilerResolver resolver = new CompilerResolver();

    /**
     * Compiles the given source files into a shared library.
     *
     * @param sources     the {@code .cpp} source files to compile
     * @param config      the compiler configuration (cache dir, compiler path, flags)
     * @param libraryName the base name of the output library (without extension)
     * @return the path to the compiled shared library
     * @throws IllegalArgumentException if {@code sources} is empty
     * @throws UncheckedIOException     if the cache directory cannot be created
     * @throws IllegalStateException    if the compiler process exits with a non-zero code
     */
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
        Path hashFile = config.cacheDir().resolve(libraryName + ".hash");

        String currentHash = computeHash(sources, config, compiler);

        if (Files.exists(output) && isHashUpToDate(hashFile, currentHash)) {
            return output;
        }

        List<String> command = new ArrayList<>();
        command.add(compiler);
        command.add("-shared");
        if (!OsUtil.isWindows()) {
            command.add("-fPIC");
        }
        command.addAll(config.compilerFlags());
        for (Path source : sources) {
            command.add(source.toString());
        }
        command.add("-o");
        command.add(output.toString());

        run(command);
        writeHash(hashFile, currentHash);
        return output;
    }

    private String computeHash(List<Path> sources, CompilerConfig config, String compiler) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            List<Path> sorted = new ArrayList<>(sources);
            sorted.sort(Comparator.comparing(Path::toString));

            for (Path source : sorted) {
                digest.update(source.toString().getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(source));
            }
            digest.update(compiler.getBytes(StandardCharsets.UTF_8));
            for (String flag : config.compilerFlags()) {
                digest.update(flag.getBytes(StandardCharsets.UTF_8));
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isHashUpToDate(Path hashFile, String currentHash) {
        if (!Files.exists(hashFile)) {
            return false;
        }
        try {
            String storedHash = Files.readString(hashFile, StandardCharsets.UTF_8).strip();
            return storedHash.equals(currentHash);
        } catch (IOException e) {
            return false; // unreadable hash file - rebuild to be safe
        }
    }

    private void writeHash(Path hashFile, String hash) {
        try {
            Files.writeString(hashFile, hash, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Runs the compiler process and waits for it to finish.
     *
     * @param command the full command line to execute
     * @throws UncheckedIOException  if the process cannot be started or its output cannot be read
     * @throws IllegalStateException if the process exits with a non-zero code, or is interrupted
     */
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

    /**
     * Returns the native shared-library file extension for the current OS.
     *
     * @return {@code ".dll"} on Windows, {@code ".dylib"} on macOS, {@code ".so"} otherwise
     */
    private String libraryExtension() {
        if (OsUtil.isWindows()) return ".dll";
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) return ".dylib"; //Dev: move into OsUtil?
        return ".so";
    }

}