package org.example.core;

import org.example.client.MiteClientRegistry;
import org.example.client.MiteClientScanner;
import org.example.compiler.CompilerConfig;
import org.example.compiler.NativeCompiler;
import org.example.linker.MiteClientProxyFactory;
import org.example.linker.NativeLibraryLinker;
import org.example.parser.CppSignatureParser;
import org.example.parser.CppSourceScanner;
import org.example.parser.NativeFunctionSignature;

import org.example.linker.NativeLibraryLinker.LinkedLibrary;

import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;


public class MiteBootstrap {

    private final MiteClientScanner clientScanner = new MiteClientScanner();
    private final CppSourceScanner cppSourceScanner = new CppSourceScanner();
    private final CppSignatureParser signatureParser = new CppSignatureParser();
    private final NativeCompiler nativeCompiler = new NativeCompiler();
    private final NativeLibraryLinker linker = new NativeLibraryLinker();
    private final HotReloadWatcher hotReloadWatcher = new HotReloadWatcher();
    private final List<LinkedLibrary> linkedLibraries = new CopyOnWriteArrayList<>();

    public MiteContext start(String basePackage) {
        return start(basePackage, CompilerConfig.defaults(), false);
    }

    public MiteContext start(String basePackage, CompilerConfig config) {
        return start(basePackage, config, false);
    }

    public MiteContext start(String basePackage, CompilerConfig config, boolean enableHotReload) {
        MiteClientRegistry clientRegistry = clientScanner.scan(basePackage);
        if (clientRegistry.getClients().isEmpty()) {
            return new MiteContext();
        }

        Map<String, MethodHandle> initialHandles = buildAndLink(config);
        AtomicReference<Map<String, MethodHandle>> handlesRef = new AtomicReference<>(initialHandles);
        MiteClientProxyFactory proxyFactory = new MiteClientProxyFactory(config.alignmentBytes());

        MiteContext context = new MiteContext();
        for (Class<?> clientInterface : clientRegistry.getClients()) {
            Object proxy = proxyFactory.create(clientInterface, handlesRef::get);
            context.put(clientInterface, proxy);
        }

        if (enableHotReload) {
            hotReloadWatcher.start(config, newHandles -> {
                handlesRef.set(newHandles.handles());
                linkedLibraries.add(newHandles);
            });
        }
        return context;
    }

    private Map<String, MethodHandle> buildAndLink(CompilerConfig config) {
        List<Path> sources = cppSourceScanner.scan(config.scriptsDir());
        List<NativeFunctionSignature> signatures = new ArrayList<>();
        for (Path source : sources) {
            signatures.addAll(signatureParser.parse(source));
        }
        Path library = nativeCompiler.compile(sources, config, "mite-native");
        LinkedLibrary linked = linker.link(library, signatures);
        linkedLibraries.add(linked);
        return linked.handles();
    }

    public void close() {
        hotReloadWatcher.close();
        for (LinkedLibrary lib : linkedLibraries) {
            try {
                lib.close();
            } catch (Exception ignored) {
            }
        }
        linkedLibraries.clear();
    }

}