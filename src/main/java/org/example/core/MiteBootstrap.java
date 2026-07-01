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

import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class MiteBootstrap {

    private final MiteClientScanner clientScanner = new MiteClientScanner();
    private final CppSourceScanner cppSourceScanner = new CppSourceScanner();
    private final CppSignatureParser signatureParser = new CppSignatureParser();
    private final NativeCompiler nativeCompiler = new NativeCompiler();
    private final NativeLibraryLinker linker = new NativeLibraryLinker();
    private final MiteClientProxyFactory proxyFactory = new MiteClientProxyFactory();

    public MiteContext start(String basePackage) {
        return start(basePackage, CompilerConfig.defaults());
    }

    public MiteContext start(String basePackage, CompilerConfig config) {
        MiteClientRegistry clientRegistry = clientScanner.scan(basePackage);
        if (clientRegistry.getClients().isEmpty()) {
            return new MiteContext();
        }

        List<Path> sources = cppSourceScanner.scan(config.scriptsDir());
        List<NativeFunctionSignature> signatures = new ArrayList<>();
        for (Path source : sources) {
            signatures.addAll(signatureParser.parse(source));
        }

        Path library = nativeCompiler.compile(sources, config, "mite-native");
        Map<String, MethodHandle> handles = linker.link(library, signatures);

        MiteContext context = new MiteContext();
        for (Class<?> clientInterface : clientRegistry.getClients()) {
            Object proxy = proxyFactory.create(clientInterface, handles);
            context.put(clientInterface, proxy);
        }
        return context;
    }

}