package org.example.core;

import org.example.compiler.CompilerConfig;
import org.example.compiler.NativeCompiler;
import org.example.linker.MiteClientProxyFactory;
import org.example.linker.NativeLibraryLinker;
import org.example.linker.NativeLibraryLinker.LinkedLibrary;
import org.example.parser.CppSignatureParser;
import org.example.parser.CppSourceScanner;
import org.example.parser.NativeFunctionSignature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
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
        Thread t = new Thread(r, "mite-hot-reload");
        t.setDaemon(true);
        return t;
    });

    private WatchService watchService;
    private volatile ScheduledFuture<?> pendingRebuild;
    private volatile boolean running;

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
        executor.submit(() -> watchLoop(config, onRebuild));
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
            key.pollEvents();
            key.reset();
            scheduleDebouncedRebuild(config, onRebuild);
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
            Path library = nativeCompiler.compile(sources, config, "mite-native");
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
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException ignored) {
        }
    }

}