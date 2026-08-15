package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.linker.NativeLibraryLinker.LinkedLibrary;
import io.github.krosta12.semimite.parser.NativeFunctionSignature;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Links a pre-built native library (.dll/.so/.dylib) without any invoking
 * the C++ compiler. Signatures are derived from @MiteClient interfaces
 * directly, in this case we don't need any .cpp files.
 */
public class PrebuiltLibraryLinker {

    private final NativeLibraryLinker linker = new NativeLibraryLinker();
    private final LanguagesTypeMapper typeMapper = new LanguagesTypeMapper();

    public LinkedLibrary link(Path libraryPath, List<Class<?>> clientInterfaces) {
        List<NativeFunctionSignature> signatures = new ArrayList<>();
        for (Class<?> iface : clientInterfaces) {
            for (Method method : iface.getDeclaredMethods()) {
                signatures.add(toSignature(method));
            }
        }
        return linker.link(libraryPath, signatures);
    }

    private NativeFunctionSignature toSignature(Method method) {
        String returnType = typeMapper.javaToCppType(method.getReturnType());

        List<NativeFunctionSignature.NativeParam> params = new ArrayList<>();
        for (var param : method.getParameters()) {
            String cppType = typeMapper.javaToCppType(param.getType());
            params.add(new NativeFunctionSignature.NativeParam(cppType, param.getName()));
        }

        return new NativeFunctionSignature(method.getName(), returnType, params, "<prebuilt>");
    }
}