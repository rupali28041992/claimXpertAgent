package com.nextgen.claims.agent;

import com.nextgen.claims.dto.DocumentRelevanceResult;
import com.nextgen.claims.dto.DocumentResult;
import com.nextgen.claims.service.FileStorageService;
import com.nextgen.claims.service.FileValidationService;
import com.nextgen.claims.service.OcrExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processes ONE uploaded file end-to-end: file validation -> OCR ->
 * document relevance. A failure at any step stops that file's pipeline but
 * is recorded on its DocumentResult rather than thrown - one bad document
 * must never fail the whole claim (rule 10/34).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAgent {

    private final FileValidationService fileValidationService;
    private final OcrExtractionService ocrExtractionService;
    private final DocumentRelevanceAgent documentRelevanceAgent;
    private final FileStorageService fileStorageService;

    public DocumentResult process(MultipartFile file, String claimId, String claimType,
                                   String claimReason, Map<String, Object> answers) {
        String documentId = "doc_" + UUID.randomUUID().toString().substring(0, 8);
        String fileName = file != null ? file.getOriginalFilename() : null;
        long start = System.currentTimeMillis();

        var fileCheck = fileValidationService.validate(file);
        if (!fileCheck.valid()) {
            log.info("claimId={} documentId={} agent=FileValidation status=FAILED errorCodes={}",
                    claimId, documentId, fileCheck.errors());
            return DocumentResult.builder()
                    .documentId(documentId).fileName(fileName)
                    .valid(false).errors(fileCheck.errors())
                    .relevant(false)
                    .status("FAILED")
                    .build();
        }
        log.info("claimId={} documentId={} agent=FileValidation status=PASS", claimId, documentId);

        String ocrText = ocrExtractionService.extractText(file);
        if (ocrText == null || ocrText.isBlank()) {
            log.info("claimId={} documentId={} agent=OCR status=OCR_FAILED", claimId, documentId);
            return DocumentResult.builder()
                    .documentId(documentId).fileName(fileName)
                    .valid(true).errors(List.of("OCR_FAILED"))
                    .relevant(false)
                    .status("FAILED")
                    .build();
        }
        log.info("claimId={} documentId={} agent=OCR status=SUCCESS", claimId, documentId);

        String fileRef = fileStorageService.store(file);

        DocumentRelevanceResult relevance = documentRelevanceAgent.evaluate(claimType, claimReason, answers, ocrText);

        List<String> errors = new ArrayList<>();
        String status;
        if (relevance.isRelated()) {
            status = "RELEVANT";
        } else {
            status = "IRRELEVANT";
            errors.add("UNRELATED_DOCUMENT");
        }

        long durationMs = System.currentTimeMillis() - start;
        log.info("claimId={} documentId={} agent=DocumentAgent status={} durationMs={}",
                claimId, documentId, status, durationMs);

        return DocumentResult.builder()
                .documentId(documentId).fileName(fileName).fileRef(fileRef)
                .valid(true).errors(errors)
                .ocrText(ocrText)
                .documentType(relevance.getDocumentType())
                .relevant(relevance.isRelated())
                .relevanceConfidence(relevance.getConfidence())
                .similarityScore(relevance.getSimilarityScore())
                .relevanceReason(relevance.getReason())
                .decisionSource(relevance.getDecisionSource())
                .status(status)
                .build();
    }
}
