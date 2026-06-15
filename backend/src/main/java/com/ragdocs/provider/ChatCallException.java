package com.ragdocs.provider;

public class ChatCallException extends RuntimeException {
    public ChatCallException(String message) {
        super(message);
    }

    public ChatCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
