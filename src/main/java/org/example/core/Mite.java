package org.example.core;

public class Mite {

    public static MiteBuilder scan(String basePackage) {
        return new MiteBuilder(basePackage);
    }

}