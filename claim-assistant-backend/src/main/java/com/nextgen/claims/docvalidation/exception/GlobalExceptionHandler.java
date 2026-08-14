package com.nextgen.claims.docvalidation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Scoped to this module's own exception types only - does not declare
 * @ControllerAdvice basePackages restrictions, but only handles
 * ClaimException, so it has no effect on the existing ClaimController's
 * error handling (which currently lets exceptions propagate to Spring
 * Boot's default error page/handler, unchanged).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ClaimException.class)
    public ResponseEntity<Map<String, String>> handleClaimException(ClaimException ex) {
        log.warn("ClaimException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage() == null ? "CLAIM_PROCESSING_ERROR" : ex.getMessage()));
    }
}
