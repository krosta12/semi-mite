package io.github.krosta12.semimite.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CppSignatureParserEdgeCasesTest {

    private final CppSignatureParser parser = new CppSignatureParser();

    @Test
    void normalizesPointerInName() {
        String src = """
                // @mite
                int scale(int *values, int count) {
                    return 0;
                }
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "test.cpp");

        assertEquals(1, result.size());
        NativeFunctionSignature.NativeParam param = result.get(0).params().get(0);
        assertEquals("int*", param.type());
        assertEquals("values", param.name());
    }

    @Test
    void ignoresFunctionWithCodeBetweenMarkerAndDefinition() {
        String src = """
                // @mite
                int x = 5;
                int notMarked(int a) {
                    return a;
                }
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "test.cpp");

        assertTrue(result.isEmpty());
    }

    @Test
    void storesSourceFileName() {
        String src = """
                // @mite
                int add(int a, int b) { return a + b; }
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "math.cpp");

        assertEquals("math.cpp", result.get(0).sourceFile());
    }

    @Test
    void parsesMultipleParamsCorrectly() {
        String src = """
                // @mite
                double compute(float x, double y, int z) {
                    return 0.0;
                }
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "test.cpp");

        assertEquals(1, result.size());
        List<NativeFunctionSignature.NativeParam> params = result.get(0).params();
        assertEquals(3, params.size());
        assertEquals("float", params.get(0).type());
        assertEquals("x", params.get(0).name());
        assertEquals("double", params.get(1).type());
        assertEquals("y", params.get(1).name());
        assertEquals("int", params.get(2).type());
        assertEquals("z", params.get(2).name());
    }

    @Test
    void parsesConstPointerParam() {
        String src = """
                // @mite
                int read(const int* data, int len) {
                    return 0;
                }
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "test.cpp");

        assertEquals(1, result.size());
        assertEquals("const int*", result.get(0).params().get(0).type());
        assertEquals("data", result.get(0).params().get(0).name());
    }

    @Test
    void handlesEmptySource() {
        List<NativeFunctionSignature> result = parser.parse("", "empty.cpp");

        assertTrue(result.isEmpty());
    }

    @Test
    void handlesSourceWithOnlyComments() {
        String src = """
                // This is a comment
                // Another comment
                """;

        List<NativeFunctionSignature> result = parser.parse(src, "test.cpp");

        assertTrue(result.isEmpty());
    }
}
