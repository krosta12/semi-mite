package org.example.core;

import org.example.compiler.CompilerConfig;
import org.example.compiler.NativeCompiler;
import org.example.linker.NativeLibraryLinker;
import org.example.linker.NativeLibraryLinker.LinkedLibrary;
import org.example.parser.CppSignatureParser;
import org.example.parser.CppSourceScanner;
import org.example.parser.NativeFunctionSignature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

    private void scheduleDebouncedRebuild(CompilerConfig config, Consumer<LinkedLibrary> onRebuild) {
        if (pendingRebuild != null) {
            pendingRebuild.cancel(false);
        }
        pendingRebuild = executor.schedule(() -> rebuild(config, onRebuild), DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }

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