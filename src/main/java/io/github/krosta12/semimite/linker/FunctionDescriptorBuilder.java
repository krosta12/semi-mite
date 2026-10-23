package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.parser.NativeFunctionSignature;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link FunctionDescriptor} (Java FFM API) from a parsed native
 * function signature, for use when linking a downcall handle.
 */
public class FunctionDescriptorBuilder {

    private final LanguagesTypeMapper typeMapper = new LanguagesTypeMapper();

    /**
     * Builds the function descriptor (argument layouts + return layout) for
     * the given native function signature.
     *
     * @param signature the parsed native function signature
     * @return the corresponding {@link FunctionDescriptor}
     * @throws IllegalArgumentException if any parameter or return type is unsupported
     */
    public FunctionDescriptor build(NativeFunctionSignature signature) {
        List<MemoryLayout> argLayouts = new ArrayList<>();
        for (NativeFunctionSignature.NativeParam param : signature.params()) {
            MemoryLayout layout = typeMapper.toLayout(param.type());
            if (layout == null) {
                throw new IllegalArgumentException(
                        "'void' is not a valid parameter type: " + signature.name() + "(...)");
            }
            argLayouts.add(layout);
        }
        MemoryLayout[] args = argLayouts.toArray(new MemoryLayout[0]);

        if (typeMapper.isVoid(signature.returnType())) {
            return FunctionDescriptor.ofVoid(args);
        }
        MemoryLayout returnLayout = typeMapper.toLayout(signature.returnType());
        return FunctionDescriptor.of(returnLayout, args);
    }

}