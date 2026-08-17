package com.nextgen.claims.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Step 2a - plain OCR/text extraction, no AI reasoning here. PDFBox handles
 * text-based PDFs. Scanned/image-only PDFs and image files (JPEG/PNG) still
 * extract to an empty string - true OCR (e.g. Tesseract/Tess4J) needs a
 * native binary beyond this module's current scope. The ValidationAgent only
 * depends on this method's return shape.
 */
@Slf4j
@Service
public class OcrExtractionService {

    public record ExtractionResult(String text, Map<String, String> fields) {
    }

    public ExtractionResult extract(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return new ExtractionResult("", Map.of());
        }
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            String text = new PDFTextStripper().getText(document);
            return new ExtractionResult(text, Map.of());
        } catch (Exception e) {
            log.warn("PDF OCR extraction failed for file={} : {}", file.getOriginalFilename(), e.getMessage());
            return new ExtractionResult("", Map.of());
        }
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
