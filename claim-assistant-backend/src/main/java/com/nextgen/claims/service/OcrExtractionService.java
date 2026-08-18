package com.nextgen.claims.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Step 2a - plain OCR/text extraction, no AI/Ollama here (rule: OCR stays
 * deterministic Java, never an LLM call). Two-tier strategy:
 *  1. PDFBox PDFTextStripper for born-digital PDFs with a real text layer.
 *  2. Tesseract (via Tess4j) rendering each page/image when step 1 returns
 *     nothing - i.e. scanned PDFs or plain image uploads (jpg/png).
 *
 * Tesseract needs the native engine + language data installed on the host
 * (tesseract.datapath / tesseract.language below); if that's missing this
 * degrades to an empty string instead of crashing the app, which the
 * caller (DocumentAgent) turns into an OCR_FAILED result for that document.
 */
@Slf4j
@Service
public class OcrExtractionService {

    @Value("${tesseract.datapath:}")
    private String tesseractDataPath;

    @Value("${tesseract.language:eng}")
    private String tesseractLanguage;

    public record ExtractionResult(String text, Map<String, String> fields) {
    }

    public ExtractionResult extract(MultipartFile file) {
        return new ExtractionResult(extractText(file), Map.of());
    }

    /** Plain OCR text, no AI reasoning. Empty string means extraction found nothing usable. */
    public String extractText(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("Could not read uploaded file {}: {}", file.getOriginalFilename(), e.getMessage());
            return "";
        }

        String contentType = file.getContentType();
        if ("application/pdf".equals(contentType)) {
            return extractFromPdf(bytes);
        }
        return extractFromImage(bytes);
    }

    private String extractFromPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            if (text != null && !text.isBlank()) {
                return text;
            }
            // No text layer -> likely a scanned PDF; OCR each rendered page.
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder combined = new StringBuilder();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 200);
                combined.append(runTesseract(image)).append('\n');
            }
            return combined.toString().strip();
        } catch (Exception e) {
            log.warn("PDF text extraction failed: {}", e.getMessage());
            return "";
        }
    }

    private String extractFromImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return "";
            }
            return runTesseract(image);
        } catch (Exception e) {
            log.warn("Image OCR failed: {}", e.getMessage());
            return "";
        }
    }

    private String runTesseract(BufferedImage image) {
        try {
            Tesseract tesseract = new Tesseract();
            if (tesseractDataPath != null && !tesseractDataPath.isBlank()) {
                tesseract.setDatapath(tesseractDataPath);
            }
            tesseract.setLanguage(tesseractLanguage);
            return tesseract.doOCR(image);
        } catch (Throwable t) {
            // Covers TesseractException and UnsatisfiedLinkError (native engine not
            // installed) - degrade gracefully rather than taking down the request.
            log.warn("Tesseract OCR unavailable ({}); returning empty text", t.getMessage());
            return "";
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
