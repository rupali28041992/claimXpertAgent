package com.nextgen.claims.docvalidation.exception;

/** Thrown for claim-level failures that should stop the whole request (e.g. bad multipart input). */
public class ClaimException extends RuntimeException {
    public ClaimException(String message) {
        super(message);
    }

    public ClaimException(String message, Throwable cause) {
        super(message, cause);
    }
}
