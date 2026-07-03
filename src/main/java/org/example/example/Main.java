package org.example.example;

import org.example.core.Mite;
import org.example.core.MiteContext;
import org.example.memory.MiteArray;

public class Main {

    public static void main(String[] args) {
        try (MiteContext context = Mite.scan("org.example.example")
                .withFlag("-O3")
                .withHotReload()
                .start()) {

            MiteArray<Float> array = MiteArray.ofFloats(1000);

            array.set(1, 43f);
            array.close();
        }
    }

}