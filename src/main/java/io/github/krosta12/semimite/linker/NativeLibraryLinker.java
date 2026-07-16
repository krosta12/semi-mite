package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.parser.NativeFunctionSignature;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Links a compiled native library: resolves each function's symbol and
 * builds a {@link MethodHandle} for it via {@link Linker#nativeLinker()}.
 */
public class NativeLibraryLinker {

    private final FunctionDescriptorBuilder descriptorBuilder = new FunctionDescriptorBuilder();

    /**
     * The result of linking a native library: a map of function name to
     * callable {@link MethodHandle}, backed by the {@link Arena} that owns
     * the loaded library's memory.
     *
     * @param handles map of native function name to its {@link MethodHandle}
     * @param arena   the arena that owns the loaded library; closing it unloads the library
     */
    public record LinkedLibrary(Map<String, MethodHandle> handles, Arena arena) implements AutoCloseable {

        /**
         * Closes the arena, unloading the native library from memory.
         */
        @Override
        public void close() {
            arena.close();
        }
    }

    /**
     * Loads the native library at {@code libraryPath} and builds a
     * {@link MethodHandle} for each given function signature.
     *
     * @param libraryPath the path to the compiled shared library
     * @param signatures  the native function signatures to link
     * @return the linked library, containing a handle per function
     * @throws IllegalStateException if a signature's symbol cannot be found in the library
     */
    public LinkedLibrary link(Path libraryPath, List<NativeFunctionSignature> signatures) {
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
        return new LinkedLibrary(handles, arena);
    }

}