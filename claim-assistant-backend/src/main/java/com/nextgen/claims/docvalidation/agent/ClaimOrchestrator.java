package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.config.DocValidationProperties;
import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.ClaimProcessingStatus;
import com.nextgen.claims.docvalidation.model.ClaimRequest;
import com.nextgen.claims.docvalidation.model.ClaimResult;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.PolicyClause;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates the complete workflow: generate claimId -> per-file
 * DocumentAgent processing (file validation + OCR + relevance, independent
 * failures) -> ONE PolicyRagAgent lookup for the whole claim (top-K
 * clauses, no LLM call) -> ONE ClaimDecisionAgent call (the only LLM call
 * in this flow) -> return result. The RAG lookup is skipped entirely when
 * no document survived DocumentAgent as valid - there is nothing for
 * retrieved clauses to be decided against, so it would just be a wasted
 * Ollama embedding call. No Mongo write happens here - nothing is
 * persisted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimOrchestrator {

    private final DocumentAgent documentAgent;
    private final PolicyRagAgent policyRagAgent;
    private final ClaimDecisionAgent claimDecisionAgent;
    private final DocValidationProperties properties;

    public ClaimResult process(ClaimRequest request, List<MultipartFile> files) {

        // STEP 1 + STEP 2
        ClaimContext context = createContext(request);
        log.info("[ClaimOrchestrator] START claim={}", context.getClaimId());

        // STEP 3: each file processed independently - one bad file never stops the others.
        for (MultipartFile file : files) {
            DocumentResult document = documentAgent.process(file, context);
            context.addDocument(document);
        }

        // STEP 4: ONE policy lookup for the whole claim, not per document - no LLM call, pure retrieval.
        // Skipped when no document survived as valid: no point retrieving clauses to
        // decide against when there is nothing left to decide over.
        boolean hasValidDocument = context.getDocuments().stream().anyMatch(DocumentResult::isValid);
        if (hasValidDocument) {
            List<PolicyClause> clauses = policyRagAgent.findRelevantClauses(
                    context.getClaimType(), context.getClaimReason(), properties.getRag().getTopK());
            context.setPolicyClauses(clauses);
        } else {
            log.info("[ClaimOrchestrator] claim={} skipping RAG lookup - no valid documents", context.getClaimId());
        }

        // STEP 5: the one and only LLM call - final decision over all valid documents + retrieved clauses together.
        context.setDecision(claimDecisionAgent.decide(context));

        // STEP 6
        context.setStatus(resolveClaimStatus(context.getDocuments()));

        log.info("[ClaimOrchestrator] COMPLETE claim={}", context.getClaimId());

        // STEP 7
        return buildResult(context);
    }

    private ClaimContext createContext(ClaimRequest request) {
        ClaimContext context = new ClaimContext();
        context.setClaimId("clm_" + UUID.randomUUID().toString().substring(0, 8));
        context.setClaimType(request.getClaimType());
        context.setClaimReason(request.getClaimReason());
        context.setAnswers(request.getAnswers() == null ? Map.of() : request.getAnswers());
        context.setStatus(ClaimProcessingStatus.RECEIVED);
        return context;
    }

    private ClaimProcessingStatus resolveClaimStatus(List<DocumentResult> documents) {
        if (documents.isEmpty()) {
            return ClaimProcessingStatus.FAILED;
        }
        boolean anySucceeded = documents.stream().anyMatch(DocumentResult::isValid);
        boolean anyFailed = documents.stream().anyMatch(d -> !d.isValid());

        if (anySucceeded && anyFailed) {
            return ClaimProcessingStatus.PARTIALLY_COMPLETED;
        }
        if (anySucceeded) {
            return ClaimProcessingStatus.COMPLETED;
        }
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
