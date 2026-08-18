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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates the complete workflow (Section 28/29 of the spec):
 * generate claimId -> per-file DocumentAgent processing (independent
 * failures) -> ONE PolicyRagAgent lookup for the whole claim (top-K
 * clauses) -> validate only documents that are valid+relevant -> ONE
 * claim-level ClaimDecisionAgent call -> persist -> return result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimOrchestrator {

    private final DocumentAgent documentAgent;
    private final PolicyRagAgent policyRagAgent;
    private final DocumentValidationAgent documentValidationAgent;
    private final ClaimDecisionAgent claimDecisionAgent;
    private final ClaimEntityRepository claimEntityRepository;
    private final DocValidationProperties properties;

    public ClaimResult process(ClaimRequest request, List<MultipartFile> files) {

        // STEP 1 + STEP 2
        ClaimContext context = createContext(request);
        log.info("[ClaimOrchestrator] START claim={}", context.getClaimId());

        // STEP 3: each file processed independently - one bad file never
        // stops the others (Section 10/34).
        for (MultipartFile file : files) {
            DocumentResult document = documentAgent.process(file, context);
            context.addDocument(document);
        }

        // STEP 4: ONE policy lookup for the whole claim, not per document (Section 23).
        List<PolicyClause> clauses = policyRagAgent.findRelevantClauses(
                context.getClaimType(), context.getClaimReason(), properties.getRag().getTopK());
        context.setPolicyClauses(clauses);
        PolicyClause bestClause = clauses.isEmpty() ? null : clauses.get(0);

        // STEP 5 + STEP 6: only documents that are valid AND relevant reach validation (Section 24).
        for (DocumentResult document : context.getDocuments()) {
            if (!document.isValid() || !document.isRelevant()) {
                continue;
            }
            log.info("[ValidationAgent] START document={}", document.getDocumentId());
            var validation = documentValidationAgent.validate(
                    bestClause, document.getDocumentType(), document.getOcrText(), context.getAnswers());
            document.setValidationResult(validation);
            document.setStatus(com.nextgen.claims.docvalidation.model.DocumentStatus.COMPLETED);
            log.info("[ValidationAgent] COMPLETE document={}", document.getDocumentId());
        }

        // STEP 6.5: ONE final claim-level decision, using all relevant documents + retrieved clauses together.
        context.setDecision(claimDecisionAgent.decide(context));

        // STEP 7
        context.setStatus(resolveClaimStatus(context.getDocuments()));

        // STEP 8
        persist(context);

        log.info("[ClaimOrchestrator] COMPLETE claim={}", context.getClaimId());

        // STEP 9
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
        boolean anySucceeded = documents.stream().anyMatch(d -> d.isValid() && d.isRelevant());
        boolean anyFailed = documents.stream().anyMatch(d -> !d.isValid() || !d.isRelevant());

        if (anySucceeded && anyFailed) {
            return ClaimProcessingStatus.PARTIALLY_COMPLETED;
        }
        if (anySucceeded) {
            return ClaimProcessingStatus.COMPLETED;
        }
        return ClaimProcessingStatus.FAILED;
    }

    private void persist(ClaimContext context) {
        ClaimEntity entity = ClaimEntity.builder()
                .claimId(context.getClaimId())
                .claimType(context.getClaimType())
                .claimReason(context.getClaimReason())
                .answers(context.getAnswers())
                .documents(context.getDocuments())
                .decision(context.getDecision())
                .status(context.getStatus())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        claimEntityRepository.save(entity);
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
