package io.github.krosta12.semimite.compiler;

/**
 * Small utility for detecting the current operating system.
 */
public class OsUtil {

    private OsUtil() {
    }

    /**
     * Checks whether the JVM is running on Windows.
     *
     * @return {@code true} if the {@code os.name} system property indicates Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

}