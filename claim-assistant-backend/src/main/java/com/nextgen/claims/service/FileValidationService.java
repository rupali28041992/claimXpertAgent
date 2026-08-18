package com.nextgen.claims.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Deterministic, non-AI file checks for the POST /api/claims pipeline
 * (DocumentAgent's first step). Never calls Ollama.
 */
@Service
public class FileValidationService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png"
    );

    @Value("${document.max-file-size:10485760}")
    private long maxFileSize;

    public record FileValidationResult(boolean valid, List<String> errors) {
        public static FileValidationResult ok() {
            return new FileValidationResult(true, List.of());
        }

        public static FileValidationResult fail(String errorCode) {
            return new FileValidationResult(false, List.of(errorCode));
        }
    }

    public FileValidationResult validate(MultipartFile file) {
        if (file == null) {
            return FileValidationResult.fail("FILE_NULL");
        }
        if (file.isEmpty()) {
            return FileValidationResult.fail("FILE_EMPTY");
        }
        if (file.getSize() > maxFileSize) {
            return FileValidationResult.fail("FILE_TOO_LARGE");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return FileValidationResult.fail("UNSUPPORTED_FILE_TYPE");
        }
        try (var in = file.getInputStream()) {
            if (in.read() == -1) {
                return FileValidationResult.fail("FILE_EMPTY");
            }
        } catch (IOException e) {
            return FileValidationResult.fail("FILE_READ_ERROR");
        }
        return FileValidationResult.ok();
    }
}
