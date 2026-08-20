package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.DocumentStatus;
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

/**
 * Processes ONE uploaded document: file validation -> OCR -> deterministic
 * keyword relevance check. No LLM call happens per document - clause-
 * satisfaction judgment is folded into ClaimDecisionAgent's single call, so
 * there is exactly one LLM call in the whole submit flow. The relevance
 * check here is plain Java (no AI) and exists purely to fail obviously
 * wrong-claim-type documents before ClaimOrchestrator spends any Ollama
 * call (RAG embedding or the decision prompt) on them. A failure at any
 * stage stops that document's own pipeline but never throws -
 * ClaimOrchestrator continues with the remaining documents regardless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAgent {

    private final FileValidationService fileValidationService;
    private final OcrService ocrService;
    private final DocumentRelevanceService documentRelevanceService;

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
                    .errors(new ArrayList<>(List.of("OCR_FAILED")))
                    .status(DocumentStatus.FAILED)
                    .build();
        }
        log.info("[DocumentAgent] OCR SUCCESS document={}", documentId);
        log.info("[DocumentAgent] OCR TEXT document={} text={}", documentId, ocrText);

        if (!documentRelevanceService.isRelevant(context.getClaimType(), ocrText)) {
            log.info("[DocumentAgent] NOT_RELEVANT document={} claimType={}", documentId, context.getClaimType());
            return DocumentResult.builder()
                    .documentId(documentId)
                    .fileName(fileName)
                    .valid(false)
                    .errors(new ArrayList<>(List.of("DOCUMENT_NOT_RELEVANT")))
                    .ocrText(ocrText)
                    .status(DocumentStatus.FAILED)
                    .build();
        }

        return DocumentResult.builder()
                .documentId(documentId)
                .fileName(fileName)
                .valid(true)
                .errors(new ArrayList<>())
                .ocrText(ocrText)
                .status(DocumentStatus.COMPLETED)
                .build();
    }
}
