package org.example.example;

import org.example.compiler.CompilerConfig;
import org.example.core.MiteBootstrap;
import org.example.core.MiteContext;

public class Main {

    public static void main(String[] args) {
        MiteBootstrap bootstrap = new MiteBootstrap();
        CompilerConfig config = CompilerConfig.defaults(); // expects ./cppScripts and ./.mite-cache

        MiteContext context = bootstrap.start("org.example.example", config, true);
        MathClient client = context.get(MathClient.class);

        System.out.println("add(2, 3) = " + client.add(2, 3));
        System.out.println("sum([1,2,3]) = " + client.sum(new float[]{1f, 2f, 3f}, 3));

        System.out.println("Hot reload active. Edit cppScripts/math.cpp and re-run client.add(...) to see changes.");
    }

}