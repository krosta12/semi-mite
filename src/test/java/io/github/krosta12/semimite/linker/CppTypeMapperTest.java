package io.github.krosta12.semimite.linker;

import org.junit.jupiter.api.Test;

import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

class CppTypeMapperTest {

    private final LanguagesTypeMapper mapper = new LanguagesTypeMapper();

    @Test
    void mapsPrimitives() {
        assertEquals(ValueLayout.JAVA_INT, mapper.toLayout("int"));
        assertEquals(ValueLayout.JAVA_FLOAT, mapper.toLayout("float"));
        assertEquals(ValueLayout.JAVA_DOUBLE, mapper.toLayout("double"));
        assertEquals(ValueLayout.JAVA_LONG, mapper.toLayout("long long"));
        assertEquals(ValueLayout.JAVA_BOOLEAN, mapper.toLayout("bool"));
    }

    @Test
    void mapsPointersToAddress() {
        assertEquals(ValueLayout.ADDRESS, mapper.toLayout("const float*"));
        assertEquals(ValueLayout.ADDRESS, mapper.toLayout("CustomDataStructure*"));
        assertTrue(mapper.isPointer("const float*"));
    }

    @Test
    void recognizesVoid() {
        assertTrue(mapper.isVoid("void"));
        assertFalse(mapper.isVoid("int"));
    }

    @Test
    void throwsOnUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toLayout("UnknownStruct"));
    }

}