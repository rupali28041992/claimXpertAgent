package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.DocumentRelevanceResult;
import com.nextgen.claims.docvalidation.model.DocumentStatus;
import com.nextgen.claims.docvalidation.service.FileValidationService;
import com.nextgen.claims.docvalidation.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Processes ONE uploaded document end to end: file validation -> OCR ->
 * relevance (Section 10 of the spec). A failure at any stage stops that
 * document's own pipeline but never throws - ClaimOrchestrator continues
 * with the remaining documents regardless (Section 34).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAgent {

    private final FileValidationService fileValidationService;
    private final OcrService ocrService;
    private final DocumentRelevanceAgent documentRelevanceAgent;

    public DocumentResult process(MultipartFile file, ClaimContext context) {
        String documentId = "doc_" + UUID.randomUUID().toString().substring(0, 8);
        String fileName = file == null ? "unknown" : file.getOriginalFilename();

        log.info("[DocumentAgent] START document={} file={}", documentId, fileName);

        var fileValidation = fileValidationService.validate(file);
        if (!fileValidation.valid()) {
            log.info("[DocumentAgent] FAIL document={} errors={}", documentId, fileValidation.errors());
            return DocumentResult.builder()
                    .documentId(documentId)
                    .fileName(fileName)
                    .valid(false)
                    .errors(fileValidation.errors())
                    .relevant(false)
                    .status(DocumentStatus.FAILED)
                    .build();
        }

        String ocrText = ocrService.extractText(file);
        if (ocrText == null) {
            log.info("[DocumentAgent] OCR_FAILED document={}", documentId);
            return DocumentResult.builder()
                    .documentId(documentId)
                    .fileName(fileName)
                    .valid(true)
                    .errors(new ArrayList<>(java.util.List.of("OCR_FAILED")))
                    .relevant(false)
                    .status(DocumentStatus.FAILED)
                    .build();
        }
        log.info("[DocumentAgent] OCR SUCCESS document={}", documentId);
        log.info("[DocumentAgent] OCR TEXT document={} text={}", documentId, ocrText);

        DocumentRelevanceResult relevance = documentRelevanceAgent.assessRelevance(
                context.getClaimType(), context.getClaimReason(), context.getAnswers(), ocrText);

        if (!relevance.isRelated()) {
            log.info("[DocumentAgent] UNRELATED_DOCUMENT document={}", documentId);
            return DocumentResult.builder()
                    .documentId(documentId)
                    .fileName(fileName)
                    .valid(true)
                    .errors(new ArrayList<>(java.util.List.of("UNRELATED_DOCUMENT")))
                    .ocrText(ocrText)
                    .documentType(relevance.getDocumentType())
                    .relevant(false)
                    .relevanceConfidence(relevance.getConfidence())
                    .similarityScore(relevance.getSimilarityScore())
                    .relevanceReason(relevance.getReason())
                    .status(DocumentStatus.IRRELEVANT)
                    .build();
        }

        log.info("[DocumentAgent] COMPLETE document={} relevant=true", documentId);
        return DocumentResult.builder()
                .documentId(documentId)
                .fileName(fileName)
                .valid(true)
                .errors(new ArrayList<>())
                .ocrText(ocrText)
                .documentType(relevance.getDocumentType())
                .relevant(true)
                .relevanceConfidence(relevance.getConfidence())
                .similarityScore(relevance.getSimilarityScore())
                .relevanceReason(relevance.getReason())
                .status(DocumentStatus.RELEVANT)
                .build();
    }
}
