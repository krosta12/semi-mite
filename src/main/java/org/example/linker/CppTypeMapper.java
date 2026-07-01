package org.example.linker;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;

public class CppTypeMapper {

    private static final Map<String, MemoryLayout> PRIMITIVES = new HashMap<>();

    static {
        PRIMITIVES.put("void", null);
        PRIMITIVES.put("int", ValueLayout.JAVA_INT);
        PRIMITIVES.put("float", ValueLayout.JAVA_FLOAT);
        PRIMITIVES.put("double", ValueLayout.JAVA_DOUBLE);
        PRIMITIVES.put("long", ValueLayout.JAVA_LONG);
        PRIMITIVES.put("short", ValueLayout.JAVA_SHORT);
        PRIMITIVES.put("char", ValueLayout.JAVA_BYTE);
        PRIMITIVES.put("bool", ValueLayout.JAVA_BOOLEAN);
    }


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