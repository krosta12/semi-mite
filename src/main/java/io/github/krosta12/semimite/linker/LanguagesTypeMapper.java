package io.github.krosta12.semimite.linker;

import io.github.krosta12.semimite.compiler.OsUtil;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps C++ type names (as strings, e.g. {@code "int"}, {@code "float"},
 * {@code "char*"}, {@code "unsigned long"}) to the corresponding
 * {@link MemoryLayout} from the Java FFM API.
 */
public class LanguagesTypeMapper {

    private static final Map<String, MemoryLayout> PRIMITIVES = new HashMap<>();

    private static final MemoryLayout LONG_LAYOUT =
            OsUtil.isWindows() ? ValueLayout.JAVA_INT : ValueLayout.JAVA_LONG;

    static {
        PRIMITIVES.put("void", null);
        PRIMITIVES.put("char", ValueLayout.JAVA_BYTE);
        PRIMITIVES.put("short", ValueLayout.JAVA_SHORT);
        PRIMITIVES.put("bool", ValueLayout.JAVA_BOOLEAN);
        PRIMITIVES.put("int", ValueLayout.JAVA_INT);
        PRIMITIVES.put("float", ValueLayout.JAVA_FLOAT);
        PRIMITIVES.put("double", ValueLayout.JAVA_DOUBLE);
        PRIMITIVES.put("long", LONG_LAYOUT);
        PRIMITIVES.put("long long", ValueLayout.JAVA_LONG);


        PRIMITIVES.put("unsigned char", ValueLayout.JAVA_BYTE);
        PRIMITIVES.put("unsigned short", ValueLayout.JAVA_SHORT);
        PRIMITIVES.put("unsigned int", ValueLayout.JAVA_INT);
        PRIMITIVES.put("unsigned long", LONG_LAYOUT);
        PRIMITIVES.put("unsigned long long", ValueLayout.JAVA_LONG);
    }


    /**
     * Converts a C++ type name to its corresponding {@link MemoryLayout}.
     *
     * @param cppType the C++ type name, e.g. {@code "int"} or {@code "char*"}
     * @return the matching layout ({@link ValueLayout#ADDRESS} for pointers,
     * {@code null} for {@code void})
     * @throws IllegalArgumentException if the type is not supported
     */
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

    /**
     * Checks whether a type is (non-pointer) {@code void}.
     *
     * @param cppType the C++ type name
     * @return {@code true} if the normalized type is exactly {@code "void"}
     */
    public boolean isVoid(String cppType) {
        String normalize = normalize(cppType);
        return normalize.equals("void") && !isPointer(normalize);
    }

    /**
     * Checks whether a type is a pointer type.
     *
     * @param cppType the C++ type name
     * @return {@code true} if the normalized type ends with {@code '*'}
     */
    public boolean isPointer(String cppType) {
        return normalize(cppType).endsWith("*");
    }

    /**
     * Normalizes a C++ type string: strips {@code const}, collapses
     * whitespace, and removes the space before a trailing {@code '*'}.
     *
     * @param cppType the raw C++ type string
     * @return the normalized type string
     */
    private String normalize(String cppType) {
        return cppType
                .replace("const", "")
                .trim()
                .replaceAll("\\s+", " ")
                .replace(" *", "*");
    }

    //more options?
    public String javaToCppType(Class<?> javaType) {
        return switch (javaType.getTypeName()) {
            case "void" -> "void";
            case "int" -> "int";
            case "long" -> "long";
            case "double" -> "double";
            case "float" -> "float";
            case "short" -> "short";
            case "byte" -> "char";
            case "boolean" -> "bool";
            default -> "void*";
        };
    }
}