package io.github.krosta12.semimite.core;

import java.nio.file.Path;

/**
 * Framework entry point for the Mite native-interop library.
 */
public class Mite {

    /**
     * Starts building a Mite runtime that will scan the given basePackage
     * for {@code @MiteClient} interfaces.
     *
     * @param basePackage the package to scan for client interfaces
     * @return a {@link MiteBuilder} for further configuration
     */
    public static MiteBuilder scan(String basePackage) {
        return new MiteBuilder(basePackage);
    }

    /**
     * Starts building a Mite runtime that will load a pre-built native
     * library (.dll / .so) — no C++ compiler required.
     *
     * @param basePackage the package to scan for @MiteClient interfaces
     * @param libraryPath path to the pre-built shared library
     */
    public static MiteBuilder use(String basePackage, Path libraryPath) {
        return new MiteBuilder(basePackage).withPrebuiltLibrary(libraryPath);
    }

}