package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.annotation.CType;
import io.github.krosta12.semimite.annotation.NativeName;
import io.github.krosta12.semimite.linker.NativeLibraryLinker.LinkedLibrary;
import io.github.krosta12.semimite.parser.NativeFunctionSignature;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
                if (method.isDefault() || Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) {
                    continue;
                }
                signatures.add(toSignature(method));
            }
        }
        return linker.link(libraryPath, signatures);
    }

    private NativeFunctionSignature toSignature(Method method) {
        String returnType = method.isAnnotationPresent(CType.class) ?
                method.getAnnotation(CType.class).type() :
                typeMapper.javaToCppType(method.getReturnType());

        List<NativeFunctionSignature.NativeParam> params = new ArrayList<>();
        for (var param : method.getParameters()) {

            CType annotationCType = param.getAnnotation(CType.class);
            String cppType = annotationCType != null ?
                    annotationCType.type() :
                    typeMapper.javaToCppType(param.getType());

            params.add(new NativeFunctionSignature.NativeParam(cppType, param.getName()));
        }

        NativeName annotationNativeName = method.getAnnotation(NativeName.class);
        String nativeName = (annotationNativeName != null) ?
                annotationNativeName.name() :
                method.getName();

        return new NativeFunctionSignature(nativeName, returnType, params, "<prebuilt>");
    }
}