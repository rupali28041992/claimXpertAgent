package com.nextgen.claims.exception;

/** Thrown for expected claim-processing failures that should map to a client-facing error code. */
public class ClaimException extends RuntimeException {
    private final String errorCode;

    public ClaimException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
