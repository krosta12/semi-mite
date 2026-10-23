package io.github.krosta12.semimite.parser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Ignored {@code @mite} markers must be reported, not dropped. */
class CppSignatureParserUnparsedMarkerTest {

    private final CppSignatureParser parser = new CppSignatureParser();

    @Test
    void markerBeforeUnparsableSignatureIsReportedWithLine() {
        List<String> warnings = new ArrayList<>();

        List<NativeFunctionSignature> signatures = parser.parse("""
                // @mite
                Foo& getRef() {
                    return x;
                }
                """, "refs.cpp", warnings::add);

        assertTrue(signatures.isEmpty(), "a reference return type cannot be parsed");
        assertEquals(1, warnings.size(), "the ignored marker must be reported");
        assertTrue(warnings.get(0).contains("refs.cpp:1"), warnings.get(0));
        assertTrue(warnings.get(0).contains("@mite"), warnings.get(0));
    }

    @Test
    void parsableSignatureProducesNoWarning() {
        List<String> warnings = new ArrayList<>();

        List<NativeFunctionSignature> signatures = parser.parse("""
                // @mite
                int add(int a, int b) {
                    return a + b;
                }
                """, "ok.cpp", warnings::add);

        assertEquals(1, signatures.size());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void commentBetweenMarkerAndFunctionIsReported() {
        List<String> warnings = new ArrayList<>();

        parser.parse("""
                // @mite
                // note
                int later(int a) {
                    return a;
                }
                """, "note.cpp", warnings::add);

        assertEquals(1, warnings.size(),
                "a marker separated from its function by a comment must be reported: " + warnings);
    }

    @Test
    void markerInsideAnotherCommentIsNotReported() {
        List<String> warnings = new ArrayList<>();

        List<NativeFunctionSignature> signatures = parser.parse("""
                int plain(int a) {
                    return a;
                } // @mite
                /* // @mite */
                """, "comments.cpp", warnings::add);

        assertTrue(signatures.isEmpty());
        assertTrue(warnings.isEmpty(), "a marker inside another comment must stay quiet: " + warnings);
    }

    @Test
    void doxygenTripleSlashMarkerIsRecognised() {
        List<String> warnings = new ArrayList<>();

        List<NativeFunctionSignature> signatures = parser.parse("""
                /// @mite
                int add(int a, int b) {
                    return a + b;
                }
                """, "doxy.cpp", warnings::add);

        assertEquals(1, signatures.size(), "/// @mite must be accepted as a marker");
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void markerWithTrailingProseIsReported() {
        List<String> warnings = new ArrayList<>();

        List<NativeFunctionSignature> signatures = parser.parse("""
                // @mite fast path
                int add(int a, int b) {
                    return a + b;
                }
                """, "almost.cpp", warnings::add);

        assertTrue(signatures.isEmpty(), "prose after the marker must not export the function");
        assertEquals(1, warnings.size(), "the user must be told why: " + warnings);
        assertTrue(warnings.get(0).contains("alone on its line"), warnings.get(0));
    }

    @Test
    void markerWithTrailingLineCommentIsAccepted() {
        List<String> warnings = new ArrayList<>();

        List<NativeFunctionSignature> signatures = parser.parse("""
                // @mite // hot path
                int add(int a, int b) {
                    return a + b;
                }
                """, "commented.cpp", warnings::add);

        assertEquals(1, signatures.size(), "a trailing // comment is allowed");
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void markerAtEndOfFileIsReported() {
        List<String> warnings = new ArrayList<>();

        parser.parse("""
                int plain(int a) {
                    return a;
                }
                // @mite
                """, "eof.cpp", warnings::add);

        assertEquals(1, warnings.size(), warnings.toString());
    }
}
