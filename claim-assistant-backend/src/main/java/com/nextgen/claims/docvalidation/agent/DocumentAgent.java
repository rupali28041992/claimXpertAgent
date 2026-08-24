package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.DocumentEvidence;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.DocumentStatus;
import com.nextgen.claims.docvalidation.service.DocumentEvidenceExtractor;
import com.nextgen.claims.docvalidation.service.DocumentRelevanceService;
import com.nextgen.claims.docvalidation.service.FileValidationService;
import com.nextgen.claims.docvalidation.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAgent {

    private final FileValidationService fileValidationService;
    private final OcrService ocrService;
    private final DocumentRelevanceService documentRelevanceService;
    private final DocumentEvidenceExtractor documentEvidenceExtractor;

    public DocumentResult process(
            MultipartFile file,
            ClaimContext context) {

        String documentId =
                "doc_" + UUID.randomUUID().toString().substring(0, 8);

        String fileName =
                file == null ? "unknown" : file.getOriginalFilename();

        log.info(
                "[DocumentAgent] START document={} file={}",
                documentId,
                fileName
        );

        // ---------------------------------------------------------
        // 1. File validation
        // ---------------------------------------------------------

        var fileValidation =
                fileValidationService.validate(file);

        if (!fileValidation.valid()) {

            log.info(
                    "[DocumentAgent] FAIL document={} errors={}",
                    documentId,
                    fileValidation.errors()
            );

            return DocumentResult.builder()
                    .documentId(documentId)
                    .fileName(fileName)
                    .valid(false)
                    .errors(fileValidation.errors())
                    .status(DocumentStatus.FAILED)
                    .build();
        }

        // ---------------------------------------------------------
        // 2. OCR
        // ---------------------------------------------------------

        String ocrText = ocrService.extractText(file);

        if (ocrText == null || ocrText.isBlank()) {

            log.info(
                    "[DocumentAgent] OCR_FAILED document={}",
                    documentId
            );

            return DocumentResult.builder()
                    .documentId(documentId)
                    .fileName(fileName)
                    .valid(true)
                    .errors(new ArrayList<>(List.of("OCR_FAILED")))
                    .status(DocumentStatus.FAILED)
                    .build();
        }

        log.info(
                "[DocumentAgent] OCR SUCCESS document={} chars={}",
                documentId,
                ocrText.length()
        );

        // DO NOT log complete OCR text.
        // It can be very large and can contain sensitive information.

        // ---------------------------------------------------------
        // 3. Deterministic relevance check
        // ---------------------------------------------------------

        boolean relevant =
                documentRelevanceService.isRelevant(
                        context.getClaimType(),
                        ocrText
                );

        if (!relevant) {

            log.info(
                    "[DocumentAgent] NOT_RELEVANT document={} claimType={}",
                    documentId,
                    context.getClaimType()
            );

            return DocumentResult.builder()
                    .documentId(documentId)
                    .fileName(fileName)
                    .valid(false)
                    .errors(new ArrayList<>(
                            List.of("DOCUMENT_NOT_RELEVANT")))
                    .status(DocumentStatus.FAILED)
                    .build();
        }

        // ---------------------------------------------------------
        // 4. Extract only useful evidence
        // ---------------------------------------------------------

        DocumentEvidence evidence =
                documentEvidenceExtractor.extract(
                        context.getClaimType(),
                        ocrText
                );

        log.info(
                "[DocumentAgent] EVIDENCE_EXTRACTED document={} type={}",
                documentId,
                evidence.getDocumentType()
        );

        // ---------------------------------------------------------
        // 5. Return ONLY structured evidence
        // ---------------------------------------------------------

        return DocumentResult.builder()
                .documentId(documentId)
                .fileName(fileName)
                .valid(true)
                .errors(new ArrayList<>())
                .evidence(evidence)
                .status(DocumentStatus.COMPLETED)
                .build();
    }
}