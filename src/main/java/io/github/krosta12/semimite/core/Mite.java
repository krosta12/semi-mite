package io.github.krosta12.semimite.core;

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

}