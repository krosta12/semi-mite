package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.compiler.CompilerConfig;
import io.github.krosta12.semimite.compiler.NativeCompiler;
import io.github.krosta12.semimite.linker.NativeLibraryLinker;
import io.github.krosta12.semimite.linker.NativeLibraryLinker.LinkedLibrary;
import io.github.krosta12.semimite.parser.CppSignatureParser;
import io.github.krosta12.semimite.parser.CppSourceScanner;
import io.github.krosta12.semimite.parser.NativeFunctionSignature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Watches the native scripts directory for filesystem changes and triggers a
 * debounced recompilation/relinking of the native library whenever a change
 * is detected.
 */
public class HotReloadWatcher implements AutoCloseable {

    private static final long DEBOUNCE_MILLIS = 300;

    private final CppSourceScanner sourceScanner = new CppSourceScanner();
    private final CppSignatureParser signatureParser = new CppSignatureParser();
    private final NativeCompiler nativeCompiler = new NativeCompiler();
    private final NativeLibraryLinker linker = new NativeLibraryLinker();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mite-hot-reload-rebuild");
        t.setDaemon(true);
        return t;
    });

    private Thread watchThread;
    private WatchService watchService;
    private volatile ScheduledFuture<?> pendingRebuild;
    private volatile boolean running;
    private final java.util.concurrent.atomic.AtomicLong buildCounter = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Starts watching {@code config.scriptsDir()} for changes. Does nothing if
     * the directory does not exist.
     *
     * @param config    the compiler configuration (scripts directory, flags, etc.)
     * @param onRebuild callback invoked with the newly linked library after each rebuild
     */
    public void start(CompilerConfig config, Consumer<LinkedLibrary> onRebuild) {
        if (!Files.isDirectory(config.scriptsDir())) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerAll(config.scriptsDir());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        running = true;
        watchThread = new Thread(() -> watchLoop(config, onRebuild), "mite-hot-reload-watch");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * Recursively registers every subdirectory of {@code root} with the watch service.
     *
     * @param root the directory tree to register
     * @throws IOException if the directory tree cannot be walked or registered
     */
    private void registerAll(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isDirectory).forEach(dir -> {
                try {
                    dir.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    /**
     * Main file-watching loop. Blocks on the watch service, registers new
     * created directories, and schedules a debounced rebuild whenever a
     * relevant change are observed. Runs until {@link #close()} is called.
     *
     * @param config    the compiler configuration
     * @param onRebuild callback invoked with the newly linked library after each rebuild
     */
    private void watchLoop(CompilerConfig config, Consumer<LinkedLibrary> onRebuild) {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            }

            Path watchedDir = (Path) key.watchable();
            boolean hasRelevantChange = false;

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    hasRelevantChange = true;
                    continue;
                }

                @SuppressWarnings("unchecked")
                Path child = watchedDir.resolve(((WatchEvent<Path>) event).context());

                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                    try {
                        registerAll(child);
                    } catch (IOException e) {
                        System.err.println("[MITE] failed watching new directory: " + child + " - " + e.getMessage());
                    }
                }
                hasRelevantChange = true;
            }

            key.reset();

            if (hasRelevantChange) {
                scheduleDebouncedRebuild(config, onRebuild);
            }
        }
    }

    /**
     * Schedules a rebuild after {@link #DEBOUNCE_MILLIS}, cancelling any
     * previously scheduled rebuild that has not yet run.
     *
     * @param config    the compiler configuration
     * @param onRebuild callback invoked with the newly linked library after the rebuild
     */
    private void scheduleDebouncedRebuild(CompilerConfig config, Consumer<LinkedLibrary> onRebuild) {
        if (pendingRebuild != null) {
            pendingRebuild.cancel(false);
        }
        pendingRebuild = executor.schedule(() -> rebuild(config, onRebuild), DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * Rescans, reparses, recompile and relink the native library, then
     * invokes {@code onRebuild} with the result. Errors are logged to
     * {@code System.err} rather than propagated, so a failed rebuild does not
     * crash the watch thread.
     *
     * @param config    the compiler configuration
     * @param onRebuild callback invoked with the newly linked library
     */
    private void rebuild(CompilerConfig config, Consumer<LinkedLibrary> onRebuild) {
        try {
            List<Path> sources = sourceScanner.scan(config.scriptsDir());
            List<NativeFunctionSignature> signatures = new ArrayList<>();
            for (Path source : sources) {
                signatures.addAll(signatureParser.parse(source));
            }
            String libraryName = "mite-native-" + buildCounter.incrementAndGet();
            Path library = nativeCompiler.compile(sources, config, libraryName);
            LinkedLibrary linked = linker.link(library, signatures);
            onRebuild.accept(linked);
        } catch (Exception e) {
            System.err.println("[MITE] hot reload failed: " + e.getMessage());
        }
    }

    /**
     * Stops the watcher: signals the watch loop to exit, shuts down the
     * rebuild executor, interrupts the watch thread, and closes the watch service.
     */
    @Override
    public void close() {
        running = false;
        executor.shutdownNow();
        if (watchThread != null) {
            watchThread.interrupt();
        }
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException ignored) {
        }
    }

}