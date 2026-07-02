package org.example.example;

import org.example.core.Mite;
import org.example.core.MiteContext;

public class Main {

    public static void main(String[] args) {
        try (MiteContext context = Mite.scan("org.example.example")
                .withFlag("-O3")
                .withHotReload()
                .start()) {

            MathClient client = context.get(MathClient.class);
            System.out.println("add(2, 3) = " + client.add(2, 3));
            System.out.println("sum([1,2,3]) = " + client.sum(new float[]{1f, 2f, 3f}, 3));

            context.close();

            System.out.println("closed");
        }
    }

}