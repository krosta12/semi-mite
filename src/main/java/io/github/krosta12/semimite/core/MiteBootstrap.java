package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.client.MiteClientRegistry;
import io.github.krosta12.semimite.client.MiteClientScanner;
import io.github.krosta12.semimite.compiler.CompilerConfig;
import io.github.krosta12.semimite.compiler.NativeCompiler;
import io.github.krosta12.semimite.linker.ManagedLibrary;
import io.github.krosta12.semimite.linker.MiteClientProxyFactory;
import io.github.krosta12.semimite.linker.NativeLibraryLinker;
import io.github.krosta12.semimite.linker.NativeLibraryLinker.LinkedLibrary;
import io.github.krosta12.semimite.linker.PrebuiltLibraryLinker;
import io.github.krosta12.semimite.parser.CppSignatureParser;
import io.github.krosta12.semimite.parser.CppSourceScanner;
import io.github.krosta12.semimite.parser.NativeFunctionSignature;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates the full startup sequence: scanning for {@code @MiteClient}
 * interfaces, scanning/parsing native sources, compiling and linking the
 * native library, creating client proxies, and optionally starting hot reload.
 */
public class MiteBootstrap implements AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(MiteBootstrap.class.getName());

    private final MiteClientScanner clientScanner = new MiteClientScanner();
    private final CppSourceScanner cppSourceScanner = new CppSourceScanner();
    private final CppSignatureParser signatureParser = new CppSignatureParser();
    private final NativeCompiler nativeCompiler = new NativeCompiler();
    private final NativeLibraryLinker linker = new NativeLibraryLinker();
    private final HotReloadWatcher hotReloadWatcher = new HotReloadWatcher();

    /**
     * Starts the runtime with default compiler configuration and hot reload disabled.
     *
     * @param basePackage the package to scan for {@code @MiteClient} interfaces
     * @return the resulting {@link MiteContext}
     */
    public MiteContext start(String basePackage) {
        return start(basePackage, CompilerConfig.defaults(), false);
    }

    /**
     * Starts the runtime with the given compiler configuration and hot reload disabled.
     *
     * @param basePackage the package to scan for {@code @MiteClient} interfaces
     * @param config      the compiler configuration to use
     * @return the resulting {@link MiteContext}
     */
    public MiteContext start(String basePackage, CompilerConfig config) {
        return start(basePackage, config, false);
    }

    /**
     * Starts the runtime: scans for client interfaces, compiles and links the
     * native library, creates a proxy for each client interface, and optionally
     * starts the hot-reload watcher.
     *
     * @param basePackage     the package to scan for {@code @MiteClient} interfaces
     * @param config          the compiler configuration to use
     * @param enableHotReload whether to watch native sources and rebuild on change
     * @return the resulting {@link MiteContext}; if no client interfaces are found,
     * an empty context is returned without compiling anything
     */
    public MiteContext start(String basePackage, CompilerConfig config, boolean enableHotReload) {
        MiteClientRegistry clientRegistry = clientScanner.scan(basePackage);
        MiteContext context = new MiteContext();
        if (clientRegistry.getClients().isEmpty()) {
            return context;
        }

        LinkedLibrary initial = buildAndLink(config);
        ManagedLibrary initialManaged = new ManagedLibrary(initial);
        context.addLibrary(initialManaged);

        AtomicReference<ManagedLibrary> currentLib = new AtomicReference<>(initialManaged);
        MiteClientProxyFactory proxyFactory = new MiteClientProxyFactory(config.pointerSize());

        for (Class<?> clientInterface : clientRegistry.getClients()) {
            context.put(clientInterface, proxyFactory.create(clientInterface, currentLib::get));
        }

        if (enableHotReload) {
            hotReloadWatcher.start(config, newLib -> {
                ManagedLibrary managed = new ManagedLibrary(newLib);
                ManagedLibrary old = currentLib.getAndSet(managed);
                context.addLibrary(managed);

                old.retire();
                context.removeLibrary(old);
            });
        }
        return context;
    }

    /**
     * Scans native sources, parses their signatures, compiles them, and links
     * the resulting library.
     *
     * @param config the compiler configuration to use
     * @return the linked native library
     */
    private LinkedLibrary buildAndLink(CompilerConfig config) {
        List<Path> sources = cppSourceScanner.scan(config.scriptsDir());
        List<NativeFunctionSignature> signatures = new ArrayList<>();
        for (Path source : sources) {
            signatures.addAll(signatureParser.parse(source,
                    message -> LOGGER.log(System.Logger.Level.WARNING, message)));
        }
        Path library = nativeCompiler.compile(sources, config, "mite-native");
        return linker.link(library, signatures);
    }

    /**
     * Stops the hot-reload watcher, if it was started.
     */
    public void close() {
        hotReloadWatcher.close();
    }

    /**
     * Start the runtime using a pre-built native library (no compilation).
     * Signatures are derived from the scanned @MiteClient interfaces.
     *
     * @param basePackage    the package to scan for @MiteClient interfaces
     * @param libraryPath    path to the pre-built .dll / .so / .dylib
     * @param pointerSize    native pointer size (typically 8)
     * @return a ready MiteContext
     */
    public MiteContext startWithLibrary(String basePackage, Path libraryPath, int pointerSize) {
        MiteClientRegistry clientRegistry = clientScanner.scan(basePackage);
        MiteContext context = new MiteContext();
        if (clientRegistry.getClients().isEmpty()) {
            return context;
        }

        PrebuiltLibraryLinker prebuiltLinker = new PrebuiltLibraryLinker();
        LinkedLibrary linked = prebuiltLinker.link(libraryPath, new ArrayList<>(clientRegistry.getClients()));
        ManagedLibrary managed = new ManagedLibrary(linked);
        context.addLibrary(managed);

        AtomicReference<ManagedLibrary> currentLib = new AtomicReference<>(managed);
        MiteClientProxyFactory proxyFactory = new MiteClientProxyFactory(pointerSize);

        for (Class<?> iface : clientRegistry.getClients()) {
            context.put(iface, proxyFactory.create(iface, currentLib::get));
        }

        return context;
    }

}