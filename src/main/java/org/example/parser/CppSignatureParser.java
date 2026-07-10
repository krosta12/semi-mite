package org.example.parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class CppSignatureParser {

    private static final Pattern MARKER = Pattern.compile("//\\s*@mite");


    private static final Pattern FUNCTION = Pattern.compile(
            "([\\w:*\\s]+?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{"
    );

    public List<NativeFunctionSignature> parse(Path cppFile) {
        String content;
        try {
            content = Files.readString(cppFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parse(content, cppFile.toString());
    }

    List<NativeFunctionSignature> parse(String content, String sourceFile) {
        List<NativeFunctionSignature> result = new ArrayList<>();
        Matcher markerMatcher = MARKER.matcher(content);
        while (markerMatcher.find()) {
            int searchFrom = markerMatcher.end();
            Matcher fnMatcher = FUNCTION.matcher(content);
            fnMatcher.region(searchFrom, content.length());
            if (fnMatcher.lookingAt() || fnMatcher.find()) {

                String between = content.substring(searchFrom, fnMatcher.start());
                if (between.isBlank()) {
                    result.add(toSignature(fnMatcher, sourceFile));
                }
            }
        }
        return result;
    }

    private NativeFunctionSignature toSignature(Matcher fnMatcher, String sourceFile) {
        String returnType = fnMatcher.group(1).trim();
        String name = fnMatcher.group(2).trim();
        String rawParams = fnMatcher.group(3).trim();
        List<NativeFunctionSignature.NativeParam> params = new ArrayList<>();
        if (!rawParams.isEmpty()) {
            for (String param : rawParams.split(",")) {
                params.add(parseParam(param.trim()));
            }
        }
        return new NativeFunctionSignature(name, returnType, params, sourceFile);
    }

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