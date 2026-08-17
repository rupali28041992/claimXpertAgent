package com.nextgen.claims.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Step 2a - plain text extraction, no AI reasoning here. Text-native PDF/DOCX only (via
 * DocumentTextExtractor / Tika) - no OCR, so image uploads (photos, scanned docs) yield empty
 * text rather than a failure; the ValidationAgent/ClaimDecisionAgent will see "no extractable
 * text" for those and can flag/route accordingly.
 */
@Service
@RequiredArgsConstructor
public class OcrExtractionService {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocumentTextExtractor documentTextExtractor;

    public record ExtractionResult(String text, Map<String, String> fields) {
    }

    public ExtractionResult extract(MultipartFile file) {
        String contentType = file.getContentType();
        boolean textNative = "application/pdf".equals(contentType) || DOCX_CONTENT_TYPE.equals(contentType);
        if (!textNative) {
            return new ExtractionResult("", Map.of()); // image/* - no OCR in this phase
        }

        try {
            String text = documentTextExtractor.extractText(file.getInputStream(), file.getOriginalFilename());
            return new ExtractionResult(text, Map.of());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Step 2b - cheap deterministic checks, no AI. Returns an error message, or null if OK. */
    public String cheapValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "File is empty";
        }
        String contentType = file.getContentType();
        boolean allowed = contentType != null
                && (contentType.startsWith("image/") || contentType.equals("application/pdf") || contentType.equals(DOCX_CONTENT_TYPE));
        if (!allowed) {
            return "Unsupported file type: " + contentType;
        }
        return null;
    }
}
