package org.example.linker;

import org.example.parser.NativeFunctionSignature;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.util.ArrayList;
import java.util.List;

public class FunctionDescriptorBuilder {

    private final CppTypeMapper typeMapper = new CppTypeMapper();

    public FunctionDescriptor build(NativeFunctionSignature signature) {
        List<MemoryLayout> argLayouts = new ArrayList<>();
        for (NativeFunctionSignature.NativeParam param : signature.params()) {
            argLayouts.add(typeMapper.toLayout(param.type()));
        }
        MemoryLayout[] args = argLayouts.toArray(new MemoryLayout[0]);

        if (typeMapper.isVoid(signature.returnType())) {
            return FunctionDescriptor.ofVoid(args);
        }
        MemoryLayout returnLayout = typeMapper.toLayout(signature.returnType());
        return FunctionDescriptor.of(returnLayout, args);
    }

}