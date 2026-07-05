package org.example.compiler;

public class OsUtil {

    private OsUtil() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

}