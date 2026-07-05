package org.example.compiler;

class OsUtil {

    private OsUtil() {}

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

}
