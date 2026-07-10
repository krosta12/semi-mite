package org.example.example;

import org.example.client.MiteClient;

@MiteClient
public interface MathClient {
    int slow_call(int x);
}