package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.DocumentEvidence;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.DocumentStatus;
import com.nextgen.claims.docvalidation.service.DocumentEvidenceExtractor;
import com.nextgen.claims.docvalidation.service.DocumentRelevanceService;
import com.nextgen.claims.docvalidation.service.FileValidationService;
import com.nextgen.claims.docvalidation.service.OcrService;
import com.nextgen.claims.docvalidation.service.OllamaEvidenceExtractor;
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
    private final OllamaEvidenceExtractor ollamaEvidenceExtractor;

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
                "[DocumentAgent] EVIDENCE_EXTRACTED document={} type={} diagnosis={} billAmount={}",
                documentId,
                evidence.getDocumentType(),
                evidence.getDiagnosis(),
                evidence.getBillAmount()
        );

        // Hybrid fallback: if regex missed critical fields, ask Ollama to fill the gaps.
        // Ollama is only called when needed — the happy path stays fast.
        if (ollamaEvidenceExtractor.needsFallback(evidence, context.getClaimType())) {
            log.info("[DocumentAgent] REGEX_INSUFFICIENT document={} — invoking Ollama extraction fallback",
                    documentId);
            evidence = ollamaEvidenceExtractor.extract(ocrText, context.getClaimType(), evidence);
            log.info("[DocumentAgent] OLLAMA_EXTRACTION_DONE document={} type={} diagnosis={} billAmount={}",
                    documentId, evidence.getDocumentType(), evidence.getDiagnosis(), evidence.getBillAmount());
        }

        // Ollama frequently returns the document type as free-form text
        // ("Discharge Summary") instead of the exact token it was asked for
        // ("DISCHARGE_SUMMARY"). Every downstream check (the type-mismatch
        // check below, and ClaimDecisionAgent's hasDischarge/hasBill flags)
        // does an exact/contains match, so an unnormalized type silently
        // causes "discharge summary not found" even when one was uploaded.
        evidence.setDocumentType(normalizeDocumentType(evidence.getDocumentType()));

        // ---------------------------------------------------------
        // 5. Cross-check declared vs detected document type.
        //    This is a deterministic rule — no AI needed.
        //    A mislabeled document is rejected here so the claim
        //    routes to MANUAL_REVIEW without Ollama guessing.
        // ---------------------------------------------------------

        String declaredCategory = context.getFileDocumentTypes().get(file.getOriginalFilename());

        if (declaredCategory != null && evidence.getDocumentType() != null) {
            String declaredNorm = normalizeDocumentType(declaredCategory);
            String detectedType = evidence.getDocumentType();

            if (!detectedType.contains(declaredNorm) && !declaredNorm.contains(detectedType)) {
                log.warn("[DocumentAgent] TYPE_MISMATCH document={} file={} declared='{}' detected='{}'",
                        documentId, fileName, declaredCategory, evidence.getDocumentType());

                return DocumentResult.builder()
                        .documentId(documentId)
                        .fileName(fileName)
                        .valid(false)
                        .errors(new ArrayList<>(List.of(
                                "Document mislabeled: uploaded under '" + declaredCategory
                                + "' but content is '" + evidence.getDocumentType() + "'")))
                        .status(DocumentStatus.FAILED)
                        .build();
            }
        }

        // ---------------------------------------------------------
        // 6. Return structured evidence
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

    /**
     * Canonicalizes a document type / declared category to a single
     * comparable token — e.g. "Discharge Summary", "discharge-summary" and
     * "DISCHARGE_SUMMARY" all become "DISCHARGE_SUMMARY". Needed because
     * Ollama's fallback extraction returns free-form text rather than the
     * exact enum-style token it was asked for.
     */
    private String normalizeDocumentType(String rawType) {
        if (rawType == null) {
            return null;
        }
        return rawType.trim()
                .toUpperCase()
                .replaceAll("[\\s\\-]+", "_")
                .replaceAll("_+", "_");
    }
}