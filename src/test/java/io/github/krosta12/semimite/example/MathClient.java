package io.github.krosta12.semimite.example;

import io.github.krosta12.semimite.client.MiteClient;

@MiteClient
public interface MathClient {
    int slow_call(int x);
}
