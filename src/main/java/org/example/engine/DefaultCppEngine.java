package org.example.engine;

import org.example.compiler.CppCompiler;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.*;

public class DefaultCppEngine implements CppEngine {

    private static final String ENTRY_POINT = "kebab_execute";

    private final CppCompiler compiler;
    private final Linker linker = Linker.nativeLinker();

    public DefaultCppEngine(CppCompiler compiler) {
        this.compiler = compiler;
    }

    @Override
    public Object execute(String sourcePathOrCode, Object... args) {
        Path lib = resolveLib(sourcePathOrCode);
        return invoke(lib, args);
    }

    private Path resolveLib(String source) {
        Path p = Path.of(source);
        if (source.endsWith(".cpp") && Files.exists(p)) return compiler.compile(p);
        return compiler.compileInline(source);
    }

    private double invoke(Path lib, Object[] args) {
        SymbolLookup lookup = SymbolLookup.libraryLookup(lib, Arena.global());

        MemorySegment fn = lookup.find(ENTRY_POINT).orElseThrow(() ->
                new IllegalStateException("function '" + ENTRY_POINT + "' does not exist. " +
                        "Add a .cpp: extern \"C\" double kebab_execute(double* data, int length);"));

        FunctionDescriptor desc = FunctionDescriptor.of(
                ValueLayout.JAVA_DOUBLE,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT
        );

        MethodHandle handle = linker.downcallHandle(fn, desc);
        double[] data = toDoubles(args);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_DOUBLE, data);
            return (double) handle.invoke(seg, data.length);
        } catch (Throwable e) {
            throw new RuntimeException("Error calling kebab_execute", e);
        }
    }

    private double[] toDoubles(Object[] args) {
        if (args.length == 1 && args[0] instanceof double[] arr) return arr;
        double[] result = new double[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Number n) result[i] = n.doubleValue();
            else throw new IllegalArgumentException("Argument #" + i + " aren't number");
        }
        return result;
    }
}