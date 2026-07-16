package io.github.krosta12.semimite.parser;

import java.util.List;

/**
 * A parsed signature of a native (C++) function.
 *
 * @param name       the function name
 * @param returnType the C++ return type as written in source
 * @param params     the function's parameters, in declaration order
 * @param sourceFile the path of the {@code .cpp} file the function was found in
 */
public record NativeFunctionSignature(
        String name,
        String returnType,
        List<NativeParam> params,
        String sourceFile
) {

    /**
     * A single function parameter.
     *
     * @param type the C++ parameter type as written in source
     * @param name the parameter name
     */
    public record NativeParam(String type, String name) {
    }

}