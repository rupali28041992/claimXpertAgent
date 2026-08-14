package com.nextgen.claims.docvalidation.service;

import com.nextgen.claims.docvalidation.config.DocValidationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic file checks only (Section 11 of the spec) - no AI/Ollama
 * calls anywhere in this class.
 */
@Service
@RequiredArgsConstructor
public class FileValidationService {

    private final DocValidationProperties properties;

    public record FileValidationResult(boolean valid, List<String> errors) {
        static FileValidationResult ok() {
            return new FileValidationResult(true, List.of());
        }

        static FileValidationResult failed(String errorCode) {
            return new FileValidationResult(false, List.of(errorCode));
        }
    }

    public FileValidationResult validate(MultipartFile file) {
        List<String> errors = new ArrayList<>();

        if (file == null) {
            return FileValidationResult.failed("FILE_NULL");
        }
        if (file.isEmpty()) {
            errors.add("FILE_EMPTY");
        }
        if (file.getContentType() == null || !properties.getDocument().getAllowedMimeTypes().contains(file.getContentType())) {
            errors.add("UNSUPPORTED_FILE_TYPE");
        }
        if (file.getSize() > properties.getDocument().getMaxFileSize()) {
            errors.add("FILE_TOO_LARGE");
        }
        try {
            // Touching the stream is the cheapest reliable way to confirm the
            // upload is actually readable without loading the whole file yet.
            file.getInputStream().close();
        } catch (Exception e) {
            errors.add("FILE_READ_ERROR");
        }

        return errors.isEmpty() ? FileValidationResult.ok() : new FileValidationResult(false, errors);
    }
}
