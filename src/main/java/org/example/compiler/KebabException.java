package org.example.compiler;

public class KebabException extends RuntimeException {
    public KebabException(String message) { super(message); }
    public KebabException(String message, Throwable cause) { super(message, cause); }
}