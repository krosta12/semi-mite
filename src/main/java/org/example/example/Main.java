package org.example.example;

import org.example.core.Mite;
import org.example.core.MiteContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

public class Main {

    public static void main(String[] args) throws Exception {
        try (MiteContext ctx = Mite.scan("org.example.example")
                .withHotReload()
                .start()) {

            MathClient client = ctx.get(MathClient.class);

            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> client.slow_call(41));

            Thread.sleep(500);
            client.slow_call(41);
            Path cpp = Path.of("cppScripts/math.cpp");
            Files.write(cpp, "\n// touch\n".getBytes(), StandardOpenOption.APPEND);
            client.slow_call(41);
            Thread.sleep(1000);

            int result = future.get();

            System.out.println("Result: " + result);
        }
    }
}