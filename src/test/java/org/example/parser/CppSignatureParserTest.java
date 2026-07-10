package org.example.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CppSignatureParserTest {

    private final CppSignatureParser parser = new CppSignatureParser();

    @Test
    void parsesSimpleMarkedFunction() {
        String src = """
                extern "C" {
                // @mite
                float calculate_metrics(const float* coordinates, int totalElements) {
                    return 0.0f;
                }
                }
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "test.cpp");

        assertEquals(1, result.size());
        NativeFunctionSignature sig = result.get(0);
        assertEquals("calculate_metrics", sig.name());
        assertEquals("float", sig.returnType());
        assertEquals(2, sig.params().size());
        assertEquals("const float*", sig.params().get(0).type());
        assertEquals("coordinates", sig.params().get(0).name());
        assertEquals("int", sig.params().get(1).type());
        assertEquals("totalElements", sig.params().get(1).name());
    }

    @Test
    void parsesVoidFunctionWithStructPointerParam() {
        String src = """
                // @mite
                void process_structures(CustomDataStructure* structure, int depth) {
                }
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "test.cpp");

        assertEquals(1, result.size());
        assertEquals("void", result.get(0).returnType());
        assertEquals("process_structures", result.get(0).name());
        assertEquals("CustomDataStructure*", result.get(0).params().get(0).type());
    }

    @Test
    void ignoresUnmarkedFunctions() {
        String src = """
                float not_exposed(float x) {
                    return x;
                }
                """;

        assertTrue(parser.parse(src, "test.cpp").isEmpty());
    }

    @Test
    void parsesFunctionWithNoParams() {
        String src = """
                // @mite
                int counter() {
                    return 1;
                }
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "test.cpp");

        assertEquals(1, result.size());
        assertTrue(result.get(0).params().isEmpty());
    }

    @Test
    void parsesMultipleMarkedFunctionsInOneFile() {
        String src = """
                // @mite
                int a() { return 1; }
                
                // @mite
                int b() { return 2; }
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "test.cpp");

        assertEquals(2, result.size());
        assertEquals("a", result.get(0).name());
        assertEquals("b", result.get(1).name());
    }

}