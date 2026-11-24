package io.github.krosta12.semimite.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the native symbol name looked up in the target library for a bound
 * method.
 *
 * <p>By default the Java method name is used verbatim as the export name. When
 * the library exports a different name - a decorated C name like
 * {@code _MessageBoxA@16}, a name that is not a valid Java identifier, or a
 * legacy symbol - declare it here:
 * <pre>{@code
 * @MiteClient
 * interface User32 {
 *     @NativeName("MessageBoxA")
 *     int messageBox(long hwnd, String text, String caption, int type);
 * }
 * }</pre>
 *
 * <p>Only the symbol lookup is affected; the Java method name still identifies
 * the proxy method. Use with {@code Mite.use(...)} - when {@code Mite.scan(...)}
 * compiles sources, the {@code extern "C"} block already pins the export name.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NativeName {

    /**
     * Exact symbol name as it appears in the library's export table.
     *
     * @return the native symbol name
     */
    String name();
}