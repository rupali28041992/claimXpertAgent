package com.nextgen.claims.docvalidation.service;

import com.nextgen.claims.docvalidation.config.DocValidationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfTextOcrService implements OcrService {

    private final DocValidationProperties properties;

    @Override
    public String extractText(MultipartFile file) {
        try {
            String contentType = file.getContentType();
            if (contentType != null && contentType.equals("application/pdf")) {
                try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                    String text = new PDFTextStripper().getText(document);
                    int maxChars = properties.getDecision().getMaxOcrCharsPerDoc();
                    if (text.length() > maxChars) {
                        log.debug("OCR text truncated from {} to {} chars for file={}",
                                text.length(), maxChars, file.getOriginalFilename());
                        text = text.substring(0, maxChars);
                    }
                    return text;
                }
            }
            return "";
        } catch (Exception e) {
            log.warn("OCR extraction failed for file={} : {}", file.getOriginalFilename(), e.getMessage());
            return "";
        }
    }
}
