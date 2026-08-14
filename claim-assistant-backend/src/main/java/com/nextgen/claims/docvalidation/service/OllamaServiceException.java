package com.nextgen.claims.docvalidation.service;

/** Never propagated as a raw Ollama/HTTP error to the frontend (Section 35/36 of the spec). */
public class OllamaServiceException extends RuntimeException {

    public enum Code {
        OLLAMA_UNAVAILABLE,
        OLLAMA_TIMEOUT,
        OLLAMA_INVALID_RESPONSE
    }

    private final Code code;

    public OllamaServiceException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
