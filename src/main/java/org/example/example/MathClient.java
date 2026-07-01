package org.example.example;

import org.example.client.MiteClient;

@MiteClient
public interface MathClient {

    int add(int a, int b);

    float sum(float[] values, int size);

}