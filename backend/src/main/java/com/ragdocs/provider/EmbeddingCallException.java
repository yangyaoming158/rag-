package com.ragdocs.provider;

public class EmbeddingCallException extends RuntimeException {
    public EmbeddingCallException(String message) {
        super(message);
    }

    public EmbeddingCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
