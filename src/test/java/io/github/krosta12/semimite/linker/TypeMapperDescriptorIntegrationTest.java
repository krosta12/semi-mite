package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.parser.NativeFunctionSignature;
import io.github.krosta12.semimite.parser.NativeFunctionSignature.NativeParam;
import org.junit.jupiter.api.Test;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class TypeMapperDescriptorIntegrationTest {

    private final LanguagesTypeMapper mapper = new LanguagesTypeMapper();
    private final FunctionDescriptorBuilder builder = new FunctionDescriptorBuilder();

    @Test
    void allPrimitiveTypesMapCorrectly() {
        assertEquals(ValueLayout.JAVA_BYTE, mapper.toLayout("char"));
        assertEquals(ValueLayout.JAVA_SHORT, mapper.toLayout("short"));
        assertEquals(ValueLayout.JAVA_INT, mapper.toLayout("int"));
        assertEquals(ValueLayout.JAVA_FLOAT, mapper.toLayout("float"));
        assertEquals(ValueLayout.JAVA_DOUBLE, mapper.toLayout("double"));
        assertEquals(ValueLayout.JAVA_LONG, mapper.toLayout("long long"));
        assertEquals(ValueLayout.JAVA_BOOLEAN, mapper.toLayout("bool"));
    }

    @Test
    void unsignedTypesMapToSameLayoutAsSigned() {
        assertEquals(ValueLayout.JAVA_BYTE, mapper.toLayout("unsigned char"));
        assertEquals(ValueLayout.JAVA_SHORT, mapper.toLayout("unsigned short"));
        assertEquals(ValueLayout.JAVA_INT, mapper.toLayout("unsigned int"));
        assertEquals(ValueLayout.JAVA_LONG, mapper.toLayout("unsigned long long"));
    }

    @Test
    void allPointerVariantsMapToAddress() {
        assertEquals(ValueLayout.ADDRESS, mapper.toLayout("int*"));
        assertEquals(ValueLayout.ADDRESS, mapper.toLayout("float*"));
        assertEquals(ValueLayout.ADDRESS, mapper.toLayout("const char*"));
        assertEquals(ValueLayout.ADDRESS, mapper.toLayout("void*"));
        assertEquals(ValueLayout.ADDRESS, mapper.toLayout("MyStruct*"));
        assertEquals(ValueLayout.ADDRESS, mapper.toLayout("const int*"));
    }

    @Test
    void voidIsNullLayout() {
        assertNull(mapper.toLayout("void"));
    }

    @Test
    void isVoidCorrect() {
        assertTrue(mapper.isVoid("void"));
        assertFalse(mapper.isVoid("void*"));
        assertFalse(mapper.isVoid("int"));
    }

    @Test
    void isPointerCorrect() {
        assertTrue(mapper.isPointer("int*"));
        assertTrue(mapper.isPointer("const char*"));
        assertTrue(mapper.isPointer("void*"));
        assertFalse(mapper.isPointer("int"));
        assertFalse(mapper.isPointer("void"));
    }

    @Test
    void constIsStrippedBeforeLookup() {
        assertEquals(ValueLayout.JAVA_INT, mapper.toLayout("const int"));
        assertEquals(ValueLayout.JAVA_FLOAT, mapper.toLayout("const float"));
    }

    @Test
    void unknownTypeThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toLayout("UnknownFoo"));
        assertThrows(IllegalArgumentException.class, () -> mapper.toLayout("std::vector"));
    }


    @Test
    void javaToCppTypeMapsBasicTypes() {
        assertEquals("void", mapper.javaToCppType(void.class));
        assertEquals("int", mapper.javaToCppType(int.class));
        assertEquals("long long", mapper.javaToCppType(long.class));
        assertEquals("double", mapper.javaToCppType(double.class));
        assertEquals("float", mapper.javaToCppType(float.class));
        assertEquals("short", mapper.javaToCppType(short.class));
        assertEquals("char", mapper.javaToCppType(byte.class));
        assertEquals("bool", mapper.javaToCppType(boolean.class));
    }

    @Test
    void cLongMatchesPlatformAbi() {
        assertEquals(Linker.nativeLinker().canonicalLayouts().get("long"),
                mapper.toLayout("long"));
    }

    @Test
    void javaToCppTypeMapsReferenceTypesToVoidPointer() {
        assertEquals("void*", mapper.javaToCppType(String.class));
        assertEquals("void*", mapper.javaToCppType(Object.class));
    }


    @Test
    void buildsDescriptorForIntAddFunction() {
        NativeFunctionSignature sig = new NativeFunctionSignature(
                "add", "int",
                List.of(new NativeParam("int", "a"), new NativeParam("int", "b")),
                "math.cpp"
        );

        FunctionDescriptor desc = builder.build(sig);

        assertEquals(ValueLayout.JAVA_INT, desc.returnLayout().orElseThrow());
        assertEquals(2, desc.argumentLayouts().size());
        assertEquals(ValueLayout.JAVA_INT, desc.argumentLayouts().get(0));
        assertEquals(ValueLayout.JAVA_INT, desc.argumentLayouts().get(1));
    }

    @Test
    void buildsVoidDescriptorWithPointerArgs() {
        NativeFunctionSignature sig = new NativeFunctionSignature(
                "process", "void",
                List.of(
                        new NativeParam("float*", "data"),
                        new NativeParam("int", "count")
                ),
                "proc.cpp"
        );

        FunctionDescriptor desc = builder.build(sig);

        assertTrue(desc.returnLayout().isEmpty(), "void return must have empty layout");
        assertEquals(2, desc.argumentLayouts().size());
        assertEquals(ValueLayout.ADDRESS, desc.argumentLayouts().get(0));
        assertEquals(ValueLayout.JAVA_INT, desc.argumentLayouts().get(1));
    }

    @Test
    void buildsDescriptorForNoArgFunction() {
        NativeFunctionSignature sig = new NativeFunctionSignature(
                "getVersion", "int", List.of(), "ver.cpp"
        );

        FunctionDescriptor desc = builder.build(sig);

        assertEquals(ValueLayout.JAVA_INT, desc.returnLayout().orElseThrow());
        assertTrue(desc.argumentLayouts().isEmpty());
    }

    @Test
    void buildsDescriptorForStringReturnFunction() {
        NativeFunctionSignature sig = new NativeFunctionSignature(
                "getMessage", "const char*", List.of(), "msg.cpp"
        );

        FunctionDescriptor desc = builder.build(sig);

        assertEquals(ValueLayout.ADDRESS, desc.returnLayout().orElseThrow());
    }

    @Test
    void buildsDescriptorForDoubleArithmeticFunction() {
        NativeFunctionSignature sig = new NativeFunctionSignature(
                "power", "double",
                List.of(new NativeParam("double", "base"), new NativeParam("double", "exp")),
                "math.cpp"
        );

        FunctionDescriptor desc = builder.build(sig);

        assertEquals(ValueLayout.JAVA_DOUBLE, desc.returnLayout().orElseThrow());
        assertEquals(ValueLayout.JAVA_DOUBLE, desc.argumentLayouts().get(0));
        assertEquals(ValueLayout.JAVA_DOUBLE, desc.argumentLayouts().get(1));
    }


    @Test
    void pipelineMapperToBuilderForMixedSignature() {
        NativeFunctionSignature sig = new NativeFunctionSignature(
                "transform", "float",
                List.of(
                        new NativeParam("const float*", "input"),
                        new NativeParam("int", "count"),
                        new NativeParam("double", "scale")
                ),
                "transform.cpp"
        );

        FunctionDescriptor desc = builder.build(sig);

        assertEquals(ValueLayout.JAVA_FLOAT, desc.returnLayout().orElseThrow());
        assertEquals(3, desc.argumentLayouts().size());
        assertEquals(ValueLayout.ADDRESS, desc.argumentLayouts().get(0));   // const float*
        assertEquals(ValueLayout.JAVA_INT, desc.argumentLayouts().get(1));  // int
        assertEquals(ValueLayout.JAVA_DOUBLE, desc.argumentLayouts().get(2)); // double
    }
}
