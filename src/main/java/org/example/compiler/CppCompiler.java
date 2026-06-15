package org.example.compiler;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CppCompiler {

    private final Path cacheDir;
    private final Map<String, CachedLib> cache = new ConcurrentHashMap<>();

    public CppCompiler(Path cacheDir) {
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            throw new KebabException("Failed to create cache directory: " + cacheDir, e);
        }
    }

    public Path compile(Path cppFile) {
        String key = cppFile.toAbsolutePath().toString();
        long lastModified = lastModified(cppFile);

        CachedLib cached = cache.get(key);
        if (cached != null && cached.timestamp == lastModified) {
            return cached.lib;
        }

        Path lib = doCompile(cppFile);
        cache.put(key, new CachedLib(lib, lastModified));
        return lib;
    }

    public Path compileInline(String code) {
        String key = "inline:" + code.hashCode();
        CachedLib cached = cache.get(key);
        if (cached != null) return cached.lib;

        try {
            Path tmp = Files.createTempFile(cacheDir, "kebab_inline_", ".cpp");
            Files.writeString(tmp, code);
            Path lib = doCompile(tmp);
            cache.put(key, new CachedLib(lib, -1));
            return lib;
        } catch (IOException e) {
            throw new KebabException("Inline code writing error", e);
        }
    }

    private Path doCompile(Path cppFile) {
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        String name = cppFile.getFileName().toString().replace(".cpp", "") + "_" + System.nanoTime();
        String libName = win ? name + ".dll" : "lib" + name + ".so";
        Path out = cacheDir.resolve(libName);

        String compiler = findCompiler();
        String[] cmd = win
                ? new String[]{compiler, "-shared", "-o", out.toString(), cppFile.toString()}
                : new String[]{compiler, "-shared", "-fPIC", "-O2", "-o", out.toString(), cppFile.toString()};

        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            if (p.waitFor() != 0) throw new KebabException("Compilation error C++:\n" + output);
            return out;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KebabException("Failed to start compiler: " + compiler, e);
        }
    }

    private String findCompiler() {
        for (String c : new String[]{"clang++", "g++"}) {
            try {
                if (new ProcessBuilder(c, "--version").start().waitFor() == 0) return c;
            } catch (Exception ignored) {}
        }
        throw new KebabException("Не найден clang++ или g++ в PATH");
    }

    private long lastModified(Path f) {
        try { return Files.getLastModifiedTime(f).toMillis(); }
        catch (IOException e) { return -1; }
    }

    private record CachedLib(Path lib, long timestamp) {}
}