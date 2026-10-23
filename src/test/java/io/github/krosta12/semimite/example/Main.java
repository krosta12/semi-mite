package io.github.krosta12.semimite.example;

import io.github.krosta12.semimite.core.Mite;
import io.github.krosta12.semimite.core.MiteContext;

/**
 * Minimal runnable example: compiles {@code cppScripts/math.cpp}, links
 * {@code slow_call} and calls it from Java.
 * <p>
 * Lives in the test sources on purpose: it is a demonstration, not part of the
 * published library. A larger set of runnable examples is kept in the separate
 * {@code semi-mite-examples} repository.
 * </p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try (MiteContext context = Mite.scan("io.github.krosta12.semimite.example").start()) {
            MathClient math = context.get(MathClient.class);
            System.out.println(math.slow_call(100));
        }
    }
}
