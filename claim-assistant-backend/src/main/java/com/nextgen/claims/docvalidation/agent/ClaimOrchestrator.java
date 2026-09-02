package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.config.DocValidationProperties;
import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.ClaimEntity;
import com.nextgen.claims.docvalidation.model.ClaimProcessingStatus;
import com.nextgen.claims.docvalidation.model.ClaimRequest;
import com.nextgen.claims.docvalidation.model.ClaimResult;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.repository.ClaimEntityRepository;
import com.nextgen.claims.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimOrchestrator {

    private final DocumentAgent documentAgent;
    private final PolicyRagAgent policyRagAgent;
    private final ClaimDecisionAgent claimDecisionAgent;
    private final DocValidationProperties properties;
    private final ClaimEntityRepository claimEntityRepository;
    private final EmailNotificationService emailNotificationService;

    /**
     * Async entry point — called from ClaimController after the 202 is returned.
     * Files must be pre-copied to ByteArrayMultipartFile before this call so
     * they remain valid after the HTTP request lifecycle ends.
     */
    @Async("claimExecutor")
    public void processAsync(String claimId, ClaimRequest request, List<MultipartFile> files) {
        MDC.put("claimId", claimId);
        try {
            updateEntityStatus(claimId, ClaimProcessingStatus.PROCESSING);
            ClaimContext context = createContextWithId(claimId, request);
            runPipeline(context, files);
            persistFinalState(context);
        } catch (Exception e) {
            log.error("[ClaimOrchestrator] claim={} unexpected pipeline failure", claimId, e);
            updateEntityStatus(claimId, ClaimProcessingStatus.FAILED);
        } finally {
            MDC.remove("claimId");
        }
    }

    private void runPipeline(ClaimContext context, List<MultipartFile> files) {
        log.info("[ClaimOrchestrator] START claim={}", context.getClaimId());

        // STEP 1: Process all documents in parallel (DocumentAgent only reads context, never mutates it)
        if (files != null && !files.isEmpty()) {
            List<DocumentResult> results = files.parallelStream()
                    .map(file -> documentAgent.process(file, context))
                    .toList();
            results.forEach(context::addDocument);
        }

        // STEP 2: Check for at least one valid document
        boolean hasValidDocument = context.getDocuments() != null
                && context.getDocuments().stream().anyMatch(DocumentResult::isValid);

        if (!hasValidDocument) {
            log.info("[ClaimOrchestrator] claim={} no valid documents", context.getClaimId());
            context.setDecision(claimDecisionAgent.decide(context));
            context.setStatus(resolveClaimStatus(context.getDocuments()));
            return;
        }

        // STEP 3: ONE RAG lookup for the whole claim
        int topK = properties.getRag().getTopK();
        List<PolicyClause> clauses = policyRagAgent.findRelevantClauses(context, topK);
        context.setPolicyClauses(clauses);
        log.info("[ClaimOrchestrator] claim={} RAG clauses={}", context.getClaimId(), clauses.size());

        // STEP 4: ONE Ollama decision call
        context.setDecision(claimDecisionAgent.decide(context));

        // STEP 5: Resolve final status
        context.setStatus(resolveClaimStatus(context.getDocuments()));

        log.info("[ClaimOrchestrator] COMPLETE claim={} decision={}", context.getClaimId(),
                context.getDecision() == null ? null : context.getDecision().getDecision());
    }

    private void persistFinalState(ClaimContext context) {
        try {
            var decision = context.getDecision();
            String aiFailureReason = (decision != null && decision.isAiError())
                    ? decision.getReason() : null;

            // Documents may all be valid, but if the AI decision failed the claim is
            // not truly COMPLETED — downgrade so the UI reflects the partial outcome.
            ClaimProcessingStatus finalStatus = context.getStatus();
            if (aiFailureReason != null && finalStatus == ClaimProcessingStatus.COMPLETED) {
                finalStatus = ClaimProcessingStatus.PARTIALLY_COMPLETED;
            }

            ClaimEntity entity = claimEntityRepository.findById(context.getClaimId())
                    .orElse(ClaimEntity.builder()
                            .claimId(context.getClaimId())
                            .claimType(context.getClaimType())
                            .claimReason(context.getClaimReason())
                            .answers(context.getAnswers())
                            .createdAt(Instant.now())
                            .build());

            entity.setDocuments(context.getDocuments());
            entity.setDecision(decision);
            entity.setStatus(finalStatus);
            entity.setAiFailureReason(aiFailureReason);
            entity.setUpdatedAt(Instant.now());
            claimEntityRepository.save(entity);

            log.info("[ClaimOrchestrator] claim={} persisted status={} aiError={}",
                    context.getClaimId(), finalStatus, aiFailureReason != null);

            if (entity.getDecision() != null && !entity.getDecision().isAiError()) {
                emailNotificationService.sendClaimDecisionEmail(entity);
            }
        } catch (Exception e) {
            log.error("[ClaimOrchestrator] claim={} failed to persist final state", context.getClaimId(), e);
        }
    }

    private void updateEntityStatus(String claimId, ClaimProcessingStatus status) {
        try {
            claimEntityRepository.findById(claimId).ifPresent(entity -> {
                entity.setStatus(status);
                entity.setUpdatedAt(Instant.now());
                claimEntityRepository.save(entity);
            });
        } catch (Exception e) {
            log.error("[ClaimOrchestrator] claim={} failed to update status={}", claimId, status, e);
        }
    }

    private ClaimContext createContextWithId(String claimId, ClaimRequest request) {
        ClaimContext context = new ClaimContext();
        context.setClaimId(claimId);
        if (request != null) {
            context.setClaimType(request.getClaimType());
            context.setClaimReason(request.getClaimReason());
            context.setAnswers(request.getAnswers() == null ? Map.of() : request.getAnswers());
            context.setFileDocumentTypes(request.getFileDocumentTypes() == null ? Map.of() : request.getFileDocumentTypes());
        } else {
            context.setAnswers(Map.of());
        }
        context.setStatus(ClaimProcessingStatus.RECEIVED);
        return context;
    }

    private ClaimProcessingStatus resolveClaimStatus(List<DocumentResult> documents) {
        if (documents == null || documents.isEmpty()) return ClaimProcessingStatus.FAILED;
        boolean anySucceeded = documents.stream().anyMatch(DocumentResult::isValid);
        boolean anyFailed = documents.stream().anyMatch(d -> !d.isValid());
        if (anySucceeded && anyFailed) return ClaimProcessingStatus.PARTIALLY_COMPLETED;
        if (anySucceeded) return ClaimProcessingStatus.COMPLETED;
        return ClaimProcessingStatus.FAILED;
    }

    private ClaimResult buildResult(ClaimContext context) {
        return ClaimResult.builder()
                .claimId(context.getClaimId())
                .status(context.getStatus())
                .documents(context.getDocuments())
                .decision(context.getDecision())
                .build();
    }
}
