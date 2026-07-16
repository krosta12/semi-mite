package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.parser.NativeFunctionSignature;
import org.junit.jupiter.api.Test;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FunctionDescriptorBuilderTest {

    private final FunctionDescriptorBuilder builder = new FunctionDescriptorBuilder();

    @Test
    void buildsDescriptorWithReturnValue() {
        NativeFunctionSignature sig = new NativeFunctionSignature(
                "add", "int",
                List.of(new NativeFunctionSignature.NativeParam("int", "a"),
                        new NativeFunctionSignature.NativeParam("int", "b")),
                "test.cpp"
        );

        FunctionDescriptor descriptor = builder.build(sig);

        assertEquals(ValueLayout.JAVA_INT, descriptor.returnLayout().orElseThrow());
        assertEquals(2, descriptor.argumentLayouts().size());
    }

    @Test
    void buildsVoidDescriptor() {
        NativeFunctionSignature sig = new NativeFunctionSignature(
                "doStuff", "void",
                List.of(new NativeFunctionSignature.NativeParam("float*", "p")),
                "test.cpp"
        );

        FunctionDescriptor descriptor = builder.build(sig);

        assertTrue(descriptor.returnLayout().isEmpty());
        assertEquals(ValueLayout.ADDRESS, descriptor.argumentLayouts().get(0));
    }

}