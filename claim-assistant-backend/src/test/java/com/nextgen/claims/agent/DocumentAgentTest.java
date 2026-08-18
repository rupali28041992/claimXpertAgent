package com.nextgen.claims.agent;

import com.nextgen.claims.dto.DocumentRelevanceResult;
import com.nextgen.claims.service.FileStorageService;
import com.nextgen.claims.service.FileValidationService;
import com.nextgen.claims.service.OcrExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DocumentAgentTest {

    private FileValidationService fileValidationService;
    private OcrExtractionService ocrExtractionService;
    private DocumentRelevanceAgent documentRelevanceAgent;
    private FileStorageService fileStorageService;
    private DocumentAgent agent;

    private final MockMultipartFile file = new MockMultipartFile("file", "bill.pdf", "application/pdf", new byte[]{1});

    @BeforeEach
    void setUp() {
        fileValidationService = Mockito.mock(FileValidationService.class);
        ocrExtractionService = Mockito.mock(OcrExtractionService.class);
        documentRelevanceAgent = Mockito.mock(DocumentRelevanceAgent.class);
        fileStorageService = Mockito.mock(FileStorageService.class);
        agent = new DocumentAgent(fileValidationService, ocrExtractionService, documentRelevanceAgent, fileStorageService);
    }

    @Test
    void invalidFileNeverReachesOcr() {
        when(fileValidationService.validate(any())).thenReturn(
                new FileValidationService.FileValidationResult(false, List.of("FILE_EMPTY")));

        var result = agent.process(file, "clm_1", "HEALTH", "HOSPITALIZATION", Map.of());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).containsExactly("FILE_EMPTY");
        assertThat(result.getStatus()).isEqualTo("FAILED");
        Mockito.verifyNoInteractions(ocrExtractionService, documentRelevanceAgent);
    }

    @Test
    void blankOcrTextMarksDocumentAsOcrFailedAndSkipsRelevance() {
        when(fileValidationService.validate(any())).thenReturn(FileValidationService.FileValidationResult.ok());
        when(ocrExtractionService.extractText(any())).thenReturn("   ");

        var result = agent.process(file, "clm_1", "HEALTH", "HOSPITALIZATION", Map.of());

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).containsExactly("OCR_FAILED");
        assertThat(result.getStatus()).isEqualTo("FAILED");
        Mockito.verifyNoInteractions(documentRelevanceAgent);
    }

    @Test
    void validRelevantDocumentIsMarkedRelevant() {
        when(fileValidationService.validate(any())).thenReturn(FileValidationService.FileValidationResult.ok());
        when(ocrExtractionService.extractText(any())).thenReturn("Apollo Hospital discharge summary");
        when(fileStorageService.store(any())).thenReturn("/uploads/abc-bill.pdf");
        when(documentRelevanceAgent.evaluate(any(), any(), any(), any())).thenReturn(
                DocumentRelevanceResult.builder()
                        .related(true).documentType("DISCHARGE_SUMMARY").confidence(0.95)
                        .similarityScore(0.95).decisionSource("VECTOR").reason("high similarity")
                        .build());

        var result = agent.process(file, "clm_1", "HEALTH", "HOSPITALIZATION", Map.of("hospitalName", "Apollo Hospital"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.isRelevant()).isTrue();
        assertThat(result.getStatus()).isEqualTo("RELEVANT");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getDocumentType()).isEqualTo("DISCHARGE_SUMMARY");
        assertThat(result.getFileRef()).isEqualTo("/uploads/abc-bill.pdf");
    }

    @Test
    void validButIrrelevantDocumentIsFlaggedAndNotSentToValidation() {
        when(fileValidationService.validate(any())).thenReturn(FileValidationService.FileValidationResult.ok());
        when(ocrExtractionService.extractText(any())).thenReturn("Car insurance policy renewal notice");
        when(fileStorageService.store(any())).thenReturn("/uploads/abc-car.pdf");
        when(documentRelevanceAgent.evaluate(any(), any(), any(), any())).thenReturn(
                DocumentRelevanceResult.builder()
                        .related(false).documentType("VEHICLE_DOCUMENT").confidence(0.9)
                        .similarityScore(0.1).decisionSource("VECTOR").reason("low similarity")
                        .build());

        var result = agent.process(file, "clm_1", "HEALTH", "HOSPITALIZATION", Map.of());

        assertThat(result.isValid()).isTrue();
        assertThat(result.isRelevant()).isFalse();
        assertThat(result.getStatus()).isEqualTo("IRRELEVANT");
        assertThat(result.getErrors()).containsExactly("UNRELATED_DOCUMENT");
    }
}
