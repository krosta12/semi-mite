package io.github.krosta12.semimite.parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * Parses C++ source files for functions marked with the {@code // @mite}
 * comment marker, extracting each function's signature (name, return type,
 * and parameters).
 */
public class CppSignatureParser {

    private static final Pattern MARKER =
            Pattern.compile("^[ \\t]*//[/!]?[ \\t]*@mite\\b", Pattern.MULTILINE);


    private static final Pattern FUNCTION = Pattern.compile(
            "([\\w:*\\s]+?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{"
    );

    /**
     * Reads a {@code .cpp} file and parses its {@code @mite}-marked function signatures.
     *
     * @param cppFile the source file to parse
     * @return the list of native function signatures found in the file
     * @throws UncheckedIOException if the file cannot be read
     */
    public List<NativeFunctionSignature> parse(Path cppFile) {
        return parse(cppFile, warning -> {
        });
    }

    /**
     * Same as {@link #parse(Path)}, but reports ignored markers to {@code onUnparsedMarker}.
     *
     * @param cppFile          the source file to parse
     * @param onUnparsedMarker receives a message for every ignored marker
     * @return the signatures found in the file
     * @throws UncheckedIOException if the file cannot be read
     */
    public List<NativeFunctionSignature> parse(Path cppFile, Consumer<String> onUnparsedMarker) {
        String content;
        try {
            content = Files.readString(cppFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parse(content, cppFile.toString(), onUnparsedMarker);
    }

    /**
     * Parses {@code @mite}-marked function signatures out of raw source text.
     * For each {@code // @mite} marker the function declaration must
     * immediately follow (only whitespace with between) for it to be recognized.
     *
     * @param content    the C++ source text
     * @param sourceFile the originating file path, recorded on each signature
     * @return the list of native function signatures found
     */
    List<NativeFunctionSignature> parse(String content, String sourceFile) {
        return parse(content, sourceFile, warning -> {
        });
    }

    /**
     * Parses the source and reports markers that do not annotate a parsable function.
     *
     * @param content          the C++ source text
     * @param sourceFile       the originating file path
     * @param onUnparsedMarker receives a message for every ignored marker
     * @return the signatures found
     */
    List<NativeFunctionSignature> parse(String content, String sourceFile, Consumer<String> onUnparsedMarker) {
        List<NativeFunctionSignature> result = new ArrayList<>();
        Matcher markerMatcher = MARKER.matcher(content);
        while (markerMatcher.find()) {
            int lineNumber = lineOf(content, markerMatcher.start());

            int lineEnd = content.indexOf('\n', markerMatcher.end());
            if (lineEnd < 0) lineEnd = content.length();
            String tail = content.substring(markerMatcher.end(), lineEnd).trim();
            if (!tail.isEmpty() && !tail.startsWith("//")) {
                onUnparsedMarker.accept(sourceFile + ":" + lineNumber
                        + ": // @mite must be alone on its line (a trailing // comment is allowed),"
                        + " marker ignored");
                continue;
            }

            int searchFrom = lineEnd;
            Matcher fnMatcher = FUNCTION.matcher(content);
            fnMatcher.region(searchFrom, content.length());

            if (fnMatcher.lookingAt()) {
                result.add(toSignature(fnMatcher, sourceFile));
                continue;
            }

            onUnparsedMarker.accept(sourceFile + ":" + lineNumber
                    + ": // @mite is not followed by a parsable function signature, marker ignored");
        }
        return result;
    }

    /** 1-based line number of a character index. */
    private static int lineOf(String content, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * Builds a {@link NativeFunctionSignature} from a matched function declaration.
     *
     * @param fnMatcher  the matcher positioned on a matched function declaration
     * @param sourceFile the originating file path
     * @return the parsed signature
     */
    private NativeFunctionSignature toSignature(Matcher fnMatcher, String sourceFile) {
        String returnType = fnMatcher.group(1).trim();
        String name = fnMatcher.group(2).trim();
        String rawParams = fnMatcher.group(3).trim();
        List<NativeFunctionSignature.NativeParam> params = new ArrayList<>();
        if (!rawParams.isEmpty() && !rawParams.equals("void")) {
            for (String param : rawParams.split(",")) {
                params.add(parseParam(param.trim()));
            }
        }
        return new NativeFunctionSignature(name, returnType, params, sourceFile);
    }

    /**
     * Splits a single raw parameter declaration into its type and name,
     * handling pointer parameters (e.g. {@code "int* x"}).
     *
     * @param param the raw parameter text, e.g. {@code "const char* name"}
     * @return the parsed parameter (type, name)
     */
    private NativeFunctionSignature.NativeParam parseParam(String param) {
        int lastSpace = param.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new NativeFunctionSignature.NativeParam(param, "");
        }
        StringBuilder type = new StringBuilder(param.substring(0, lastSpace).trim());
        String name = param.substring(lastSpace + 1).trim();

        while (name.startsWith("*")) {
            type.append("*");
            name = name.substring(1);
        }
        return new NativeFunctionSignature.NativeParam(type.toString(), name);
    }

}