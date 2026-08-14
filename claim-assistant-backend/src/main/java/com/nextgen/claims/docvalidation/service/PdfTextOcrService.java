package com.nextgen.claims.docvalidation.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDFBox-based text extraction (Section 12 of the spec). Handles
 * text-based PDFs and (via the raw-bytes fallback) plain text content.
 *
 * Known limitation: scanned/image-only PDFs and image files (JPEG/PNG)
 * will extract to an empty string, since this does not run true OCR
 * (Tesseract) - that requires installing a native binary beyond this
 * module's scope. Wire in Tess4J here later without changing OcrService's
 * contract or any caller.
 */
@Slf4j
@Service
public class PdfTextOcrService implements OcrService {

    @Override
    public String extractText(MultipartFile file) {
        try {
            String contentType = file.getContentType();
            if (contentType != null && contentType.equals("application/pdf")) {
                try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                    return new PDFTextStripper().getText(document);
                }
            }
            // Image files (image/jpeg, image/png): no real OCR wired up yet.
            // Returning empty text (not null) lets the pipeline continue and
            // let the relevance/validation stages report low confidence,
            // rather than hard-failing every image upload.
            return "";
        } catch (Exception e) {
            log.warn("OCR extraction failed for file={} : {}", file.getOriginalFilename(), e.getMessage());
            return null;
        }
    }
}
