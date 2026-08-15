package io.github.krosta12.semimite.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class CppSignatureParserIntegrationTest {

    private final CppSignatureParser parser = new CppSignatureParser();


    @Test
    void parseRealFileWithMultipleFunctions(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("math.cpp");
        Files.writeString(src, """
                #include <cmath>
                
                extern "C" {
                
                // @mite
                int add(int a, int b) {
                    return a + b;
                }
                
                // @mite
                double sqrt_approx(double x) {
                    return std::sqrt(x);
                }
                
                double not_exposed(double x) {
                    return x * 2.0;
                }
                
                }
                """);

        List<NativeFunctionSignature> sigs = parser.parse(src);

        assertEquals(2, sigs.size());

        NativeFunctionSignature add = sigs.get(0);
        assertEquals("add", add.name());
        assertEquals("int", add.returnType());
        assertEquals(2, add.params().size());
        assertEquals("int", add.params().get(0).type());
        assertEquals("a", add.params().get(0).name());
        assertEquals("int", add.params().get(1).type());
        assertEquals("b", add.params().get(1).name());

        NativeFunctionSignature sqrt = sigs.get(1);
        assertEquals("sqrt_approx", sqrt.name());
        assertEquals("double", sqrt.returnType());
        assertEquals(1, sqrt.params().size());
        assertEquals("double", sqrt.params().get(0).type());
        assertEquals("x", sqrt.params().get(0).name());
    }


    @Test
    void parseFileWithNoMarkers(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("internal.cpp");
        Files.writeString(src, """
                int helper(int x) { return x + 1; }
                float scale(float v, float factor) { return v * factor; }
                """);

        List<NativeFunctionSignature> sigs = parser.parse(src);
        assertTrue(sigs.isEmpty(), "File without @mite markers must yield no signatures");
    }


    @Test
    void signatureRecordsSourceFilePath(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("traced.cpp");
        Files.writeString(src, """
                // @mite
                int probe() { return 0; }
                """);

        List<NativeFunctionSignature> sigs = parser.parse(src);
        assertEquals(1, sigs.size());
        assertEquals(src.toString(), sigs.get(0).sourceFile());
    }

    @Test
    void parsesComplexParameterTypes() {
        String src = """
                // @mite
                void process(const float* data, int count, double threshold) {
                }
                """;

        List<NativeFunctionSignature> sigs = parser.parse(src, "proc.cpp");

        assertEquals(1, sigs.size());
        NativeFunctionSignature sig = sigs.get(0);
        assertEquals("void", sig.returnType());
        assertEquals("process", sig.name());
        assertEquals(3, sig.params().size());
        assertEquals("const float*", sig.params().get(0).type());
        assertEquals("data", sig.params().get(0).name());
        assertEquals("int", sig.params().get(1).type());
        assertEquals("count", sig.params().get(1).name());
        assertEquals("double", sig.params().get(2).type());
        assertEquals("threshold", sig.params().get(2).name());
    }


    @Test
    void parsesPointerAttachedToParameterName() {
        String src = """
                // @mite
                void fill(int *buffer, int size) {
                }
                """;

        List<NativeFunctionSignature> sigs = parser.parse(src, "fill.cpp");

        assertEquals(1, sigs.size());
        NativeFunctionSignature sig = sigs.get(0);
        assertEquals("int*", sig.params().get(0).type());
        assertEquals("buffer", sig.params().get(0).name());
    }


    @Test
    void parsesNoParamFunction() {
        String src = """
                // @mite
                int counter() {
                    return 1;
                }
                """;

        List<NativeFunctionSignature> sigs = parser.parse(src, "c.cpp");

        assertEquals(1, sigs.size());
        assertTrue(sigs.get(0).params().isEmpty());
        assertEquals("counter", sigs.get(0).name());
    }

    @Test
    void markerWithNonBlankContentBeforeFunctionIsIgnored() {
        String src = """
                // @mite
                // some comment in between
                int sneaky(int x) {
                    return x;
                }
                """;

        List<NativeFunctionSignature> sigs = parser.parse(src, "sneaky.cpp");
        assertTrue(sigs.isEmpty(), "non-blank content between @mite and function must be ignored");
    }


    @Test
    void multipleMiteMarkersInFile() {
        String src = """
                // @mite
                int alpha(int x) { return x; }
                
                // @mite
                float beta(float y) { return y; }
                
                // @mite
                void gamma() { }
                """;

        List<NativeFunctionSignature> sigs = parser.parse(src, "multi.cpp");

        assertEquals(3, sigs.size());
        assertEquals("alpha", sigs.get(0).name());
        assertEquals("int", sigs.get(0).returnType());
        assertEquals("beta", sigs.get(1).name());
        assertEquals("float", sigs.get(1).returnType());
        assertEquals("gamma", sigs.get(2).name());
        assertEquals("void", sigs.get(2).returnType());
    }

    @Test
    void parsesPointerReturnType() {
        String src = """
                // @mite
                const char* getMessage() {
                    return "hello";
                }
                """;

        List<NativeFunctionSignature> sigs = parser.parse(src, "msg.cpp");

        assertEquals(1, sigs.size());

        assertTrue(sigs.get(0).returnType().contains("char"),
                "return type must reference 'char'");
        assertTrue(sigs.get(0).returnType().contains("*"),
                "return type must be a pointer");
    }


    @Test
    void parsesStructPointerParam() {
        String src = """
                // @mite
                void transform(MyVector3* v) {
                }
                """;

        List<NativeFunctionSignature> sigs = parser.parse(src, "geo.cpp");

        assertEquals(1, sigs.size());
        assertEquals("MyVector3*", sigs.get(0).params().get(0).type());
        assertEquals("v", sigs.get(0).params().get(0).name());
    }


    @Test
    void signatureRecordEquality() {
        var p1 = new NativeFunctionSignature.NativeParam("int", "x");
        var p2 = new NativeFunctionSignature.NativeParam("int", "x");
        assertEquals(p1, p2);

        var s1 = new NativeFunctionSignature("foo", "int", List.of(p1), "f.cpp");
        var s2 = new NativeFunctionSignature("foo", "int", List.of(p2), "f.cpp");
        assertEquals(s1, s2);
    }

    @Test
    void parseNonExistentFileThrows(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("ghost.cpp");
        assertThrows(java.io.UncheckedIOException.class, () -> parser.parse(missing));
    }
}
