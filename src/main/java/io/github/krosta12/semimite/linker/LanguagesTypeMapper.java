package io.github.krosta12.semimite.linker;

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps C++ type names (as strings, e.g. {@code "int"}, {@code "float"},
 * {@code "char*"}, {@code "unsigned long"}) to the corresponding
 * {@link MemoryLayout} from the Java FFM API.
 * <p>
 * Sizes that differ between platforms ({@code long}, {@code size_t},
 * {@code wchar_t}, {@code bool}) are taken from
 * {@link Linker#canonicalLayouts()}, i.e. from the same ABI description the
 * native linker itself uses for the downcall. Requires JDK 22+.
 */
public class LanguagesTypeMapper {

    private static final Map<String, MemoryLayout> PRIMITIVES = new HashMap<>();

    static {
        Map<String, MemoryLayout> abi = Linker.nativeLinker().canonicalLayouts();
        MemoryLayout longLayout = abi.get("long");
        MemoryLayout sizeLayout = abi.get("size_t");

        PRIMITIVES.put("bool", abi.get("bool"));
        PRIMITIVES.put("wchar_t", abi.get("wchar_t"));

        put(ValueLayout.JAVA_BYTE, "char", "signed char", "unsigned char", "int8_t", "uint8_t");

        put(ValueLayout.JAVA_SHORT, "short", "short int", "signed short", "unsigned short", "unsigned short int", "int16_t", "uint16_t");

        put(ValueLayout.JAVA_INT, "int", "signed int", "signed", "unsigned", "unsigned int", "int32_t", "uint32_t");

        put(ValueLayout.JAVA_FLOAT, "float");
        put(ValueLayout.JAVA_DOUBLE, "double");

        put(longLayout, "long", "long int", "signed long", "unsigned long", "unsigned long int");
        put(ValueLayout.JAVA_LONG, "long long", "long long int", "signed long long", "unsigned long long", "unsigned long long int", "int64_t", "uint64_t");

        put(sizeLayout, "size_t", "ssize_t", "ptrdiff_t", "intptr_t", "uintptr_t");
    }

    private static void put(MemoryLayout layout, String... names) {
        for (String name : names) {
            PRIMITIVES.put(name, layout);
        }
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
        return normalize(cppType).equals("void");
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
                .replaceAll("\\b(const|volatile)\\b", "")
                .replace('&', '*')
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll(" ?\\*", "*");
    }

    /**
     * Maps a Java type to the C type name used for the native signature.
     * Java {@code long} is always 8 bytes, so it maps to {@code long long}:
     * for a C {@code long} on Windows (4 bytes) declare the Java parameter as {@code int}.
     *
     * @param javaType the Java parameter or return type
     * @return the C type name; {@code "void*"} for all reference types
     * @throws IllegalArgumentException for Java {@code char}, which has no C equivalent
     */
    public String javaToCppType(Class<?> javaType) {
        if (javaType == char.class) {
            throw new IllegalArgumentException(
                    "Java 'char' is 2 bytes and has no C equivalent; use 'byte' for C char or 'short' for 16-bit value");
        }
        return switch (javaType.getTypeName()) {
            case "void" -> "void";
            case "int" -> "int";
            case "long" -> "long long";
            case "double" -> "double";
            case "float" -> "float";
            case "short" -> "short";
            case "byte" -> "char";
            case "boolean" -> "bool";
            default -> "void*";
        };
    }
}