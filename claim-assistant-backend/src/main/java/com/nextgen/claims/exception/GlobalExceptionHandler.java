package com.nextgen.claims.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Never exposes raw stack traces / Ollama errors to the frontend - always a structured errorCode + message. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ClaimException.class)
    public ResponseEntity<Map<String, Object>> handleClaimException(ClaimException ex) {
        log.warn("errorCode={} message={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("errorCode", ex.getErrorCode(), "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("errorCode", "NOT_FOUND", "message", ex.getMessage()));
    }
}
