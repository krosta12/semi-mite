package io.github.krosta12.semimite.parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds all {@code .cpp} files under a given directory, recursively.
 */
public class CppSourceScanner {

    /**
     * Scans {@code scriptsDir} recursively for {@code .cpp} files.
     *
     * @param scriptsDir the directory to scan
     * @return a sorted list of paths to all {@code .cpp} files found;
     *         an empty list if {@code scriptsDir} does not exist or is not a directory
     */
    public List<Path> scan(Path scriptsDir) {
        if (!Files.isDirectory(scriptsDir)) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(scriptsDir)) {
            walk.filter(p -> p.toString().endsWith(".cpp"))
                    .sorted()
                    .forEach(result::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

}