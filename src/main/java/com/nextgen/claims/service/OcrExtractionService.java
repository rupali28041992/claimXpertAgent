package com.nextgen.claims.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Step 2a - plain OCR/text extraction, no AI reasoning here. Swap the body
 * for a real OCR library (Tesseract, cloud Vision API, etc.) later; the
 * ValidationAgent only depends on this method's return shape.
 */
@Service
public class OcrExtractionService {

    public record ExtractionResult(String text, Map<String, String> fields) {
    }

    public ExtractionResult extract(MultipartFile file) {
        // Placeholder: wire in a real OCR call here.
        String text = "OCR extraction not yet wired up for file: " + file.getOriginalFilename();
        return new ExtractionResult(text, Map.of());
    }

    /** Step 2b - cheap deterministic checks, no AI. Returns an error message, or null if OK. */
    public String cheapValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "File is empty";
        }
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.startsWith("image/") || contentType.equals("application/pdf"))) {
            return "Unsupported file type: " + contentType;
        }
        return null;
    }
}
