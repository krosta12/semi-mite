package org.example.parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class CppSourceScanner {

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