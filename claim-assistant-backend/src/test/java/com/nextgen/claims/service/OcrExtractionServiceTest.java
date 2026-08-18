package com.nextgen.claims.service;

import com.nextgen.claims.support.TestPdfBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the deterministic (no-Ollama) PDFBox text-extraction path. The
 * Tesseract fallback for scanned PDFs/images isn't covered here since it
 * needs the native Tesseract engine installed on the host - it degrades to
 * an empty string (verified by ocrOfGarbageImageReturnsEmptyText below)
 * rather than throwing, which is the behavior DocumentAgent relies on.
 */
class OcrExtractionServiceTest {

    private final OcrExtractionService service = new OcrExtractionService();

    @Test
    void extractsTextFromBornDigitalPdf() throws Exception {
        byte[] pdfBytes = TestPdfBuilder.withText("Apollo Hospital Discharge Summary. Patient: John Doe. Admission Date: 2026-07-10.");
        var file = new MockMultipartFile("file", "discharge.pdf", "application/pdf", pdfBytes);

        String text = service.extractText(file);

        assertThat(text).contains("Apollo Hospital").contains("John Doe");
    }

    @Test
    void ocrOfGarbageImageReturnsEmptyTextInsteadOfThrowing() {
        var file = new MockMultipartFile("file", "not-an-image.png", "image/png", new byte[]{1, 2, 3, 4});

        String text = service.extractText(file);

        assertThat(text).isEmpty();
    }

    @Test
    void cheapValidateRejectsEmptyFile() {
        var file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThat(service.cheapValidate(file)).isEqualTo("File is empty");
    }

    @Test
    void cheapValidateAcceptsPdf() {
        var file = new MockMultipartFile("file", "bill.pdf", "application/pdf", new byte[]{1});
        assertThat(service.cheapValidate(file)).isNull();
    }
}
