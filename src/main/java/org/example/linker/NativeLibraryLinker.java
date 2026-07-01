package org.example.linker;

import org.example.parser.NativeFunctionSignature;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class NativeLibraryLinker {

    private final FunctionDescriptorBuilder descriptorBuilder = new FunctionDescriptorBuilder();

    public Map<String, MethodHandle> link(Path libraryPath, List<NativeFunctionSignature> signatures) {
        Arena arena = Arena.ofShared();
        SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath, arena);
        Linker linker = Linker.nativeLinker();

        Map<String, MethodHandle> handles = new HashMap<>();
        for (NativeFunctionSignature signature : signatures) {
            var symbol = lookup.find(signature.name())
                    .orElseThrow(() -> new IllegalStateException(
                            "Symbol not found in native library: " + signature.name()));
            FunctionDescriptor descriptor = descriptorBuilder.build(signature);
            MethodHandle handle = linker.downcallHandle(symbol, descriptor);
            handles.put(signature.name(), handle);
        }
        return handles;
    }

}