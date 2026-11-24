package io.github.krosta12.semimite.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies an explicit C or C++ type expression for a bound method return value
 * or parameter when the default Java-to-native type mapping is insufficient.
 *
 * <p>The string is normalized ({@code const}/{@code volatile} stripped,
 * {@code &} treated as {@code *}, whitespace collapsed), then resolved through
 * {@code @DefineTypeMap} aliases and the Panama ABI table
 * ({@link java.lang.foreign.Linker#canonicalLayouts()}). If it resolves to
 * neither, the link fails with an {@link IllegalArgumentException} instead of
 * silently producing a wrong ABI.
 *
 * <p>Typical use - a C {@code long} (4 bytes on Windows, 8 on Linux/macOS),
 * which the default mapping cannot express because Java {@code long} is always
 * 8 bytes:
 * <pre>{@code
 * @CType("long") long someWin32Call(@CType("long") long value);
 * }</pre>
 *
 * <p>Only types the FFM linker can marshal are supported. {@code long double},
 * {@code __int128}, and SIMD vectors have no Panama representation; wrap them
 * in a C++ shim.
 */
@Documented
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CType {

    /**
     * C or C++ type expression (for example, {@code "const char*"} or
     * {@code "size_t"}).
     *
     * @return the declared native type expression
     */
    String type();
}