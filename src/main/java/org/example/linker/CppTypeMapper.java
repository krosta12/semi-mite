package org.example.linker;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.Map;

public class CppTypeMapper {

    private static final Map<String, MemoryLayout> PRIMITIVES = Map.ofEntries(
            Map.entry("void", null),
            Map.entry("int", ValueLayout.JAVA_INT),
            Map.entry("float", ValueLayout.JAVA_FLOAT),
            Map.entry("double", ValueLayout.JAVA_DOUBLE),
            Map.entry("long", ValueLayout.JAVA_LONG),
            Map.entry("short", ValueLayout.JAVA_SHORT),
            Map.entry("char", ValueLayout.JAVA_BYTE),
            Map.entry("bool", ValueLayout.JAVA_BOOLEAN)
    );


    public MemoryLayout toLayout(String cppType) {
        String normalized = normalize(cppType);
        if (isPointer(normalized)) {
            return ValueLayout.ADDRESS;
        }
        MemoryLayout layout = PRIMITIVES.get(normalized);
        if (layout == null && !normalized.equals("void")) {
            throw new IllegalArgumentException("Unsupported cpp type: " + cppType);
        }
        return layout;
    }

    public boolean isVoid(String cppType) {
        return normalize(cppType).equals("void") && !isPointer(normalize(cppType));
    }

    public boolean isPointer(String cppType) {
        return normalize(cppType).endsWith("*");
    }

    private String normalize(String cppType) {
        return cppType
                .replace("const", "")
                .trim()
                .replaceAll("\\s+", " ")
                .replace(" *", "*");
    }
}