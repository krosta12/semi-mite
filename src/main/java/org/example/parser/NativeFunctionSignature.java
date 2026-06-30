package org.example.parser;

import java.util.List;

public record NativeFunctionSignature(
        String name,
        String returnType,
        List<NativeParam> params,
        String sourceFile
) {

    public record NativeParam(String type, String name) {
    }

}