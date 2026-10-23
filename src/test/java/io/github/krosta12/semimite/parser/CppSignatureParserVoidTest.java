package io.github.krosta12.semimite.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** {@code int f(void)} must parse as a function without parameters. */
class CppSignatureParserVoidTest {

    private final CppSignatureParser parser = new CppSignatureParser();

    @Test
    void voidParameterIsTreatedAsNoParameters() {
        List<NativeFunctionSignature> signatures = parser.parse("""
                extern "C" {
                // @mite
                int f(void) {
                    return 1;
                }
                }
                """, "test.cpp");

        assertEquals(1, signatures.size());
        assertTrue(signatures.get(0).params().isEmpty(), "f(void) must yield zero parameters");
    }

    @Test
    void realParametersAreStillParsed() {
        List<NativeFunctionSignature> signatures = parser.parse("""
                // @mite
                int g(int a, float b) {
                    return a;
                }
                """, "test.cpp");

        assertEquals(1, signatures.size());
        assertEquals(2, signatures.get(0).params().size());
        assertEquals("int", signatures.get(0).params().get(0).type());
        assertEquals("float", signatures.get(0).params().get(1).type());
    }
}
