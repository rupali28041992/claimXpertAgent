package com.nextgen.claims.agent;

import com.nextgen.claims.dto.ClaimContext;
import com.nextgen.claims.dto.ClaimDecision;
import com.nextgen.claims.dto.ClaimRequest;
import com.nextgen.claims.dto.ClaimResult;
import com.nextgen.claims.dto.DocumentResult;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.ClaimAnswer;
import com.nextgen.claims.model.ClaimDocument;
import com.nextgen.claims.model.ClaimStatus;
import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.model.StatusChange;
import com.nextgen.claims.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates the full POST /api/claims workflow:
 *   1. generate claimId
 *   2. DocumentAgent per uploaded file (file validation -> OCR -> relevance), independently
 *   3. PolicyRagAgent ONCE per claim
 *   4. ValidationAgent for every valid + relevant document, reusing the same policy clauses
 *   5. persist to the existing `claims` collection (Claim/ClaimDocument), no new collection
 *   6. return ClaimResult
 *
 * A single document failing never aborts the others (rule 10/34) - each
 * DocumentAgent.process(...) call is independent and always returns a result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimOrchestrator {

    private final ClaimRepository claimRepository;
    private final DocumentAgent documentAgent;
    private final PolicyRagAgent policyRagAgent;
    private final ValidationAgent validationAgent;
    private final ClaimDecisionAgent claimDecisionAgent;

    public ClaimResult process(ClaimRequest request, List<MultipartFile> files) {
        String claimId = "clm_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("claimId={} agent=ClaimOrchestrator status=START", claimId);

        ClaimContext context = ClaimContext.builder()
                .claimId(claimId)
                .claimType(request.getClaimType())
                .claimReason(request.getClaimReason())
                .answers(request.getAnswers())
                .status("PROCESSING")
                .build();

        for (MultipartFile file : files) {
            DocumentResult document = documentAgent.process(
                    file, claimId, context.getClaimType(), context.getClaimReason(), context.getAnswers());
            context.addDocument(document);
        }

        // ONE policy RAG lookup per claim, reused for every relevant document below.
        List<PolicyClauseVector> clauses =
                policyRagAgent.findClauses(context.getClaimType(), context.getClaimReason());
        context.setPolicyClauses(clauses);
        context.setPolicyClause(clauses.isEmpty() ? null : clauses.get(0));

        List<ClaimAnswer> answerList = toClaimAnswers(context.getAnswers());

        for (DocumentResult document : context.getDocuments()) {
            if (!document.isValid() || !document.isRelevant()) {
                continue; // invalid file or irrelevant document - never reaches ValidationAgent (rule 24)
            }
            log.info("claimId={} documentId={} agent=ValidationAgent status=START",
                    claimId, document.getDocumentId());
            ValidationResult result = validationAgent.validate(
                    clauses,
                    context.getClaimType(),
                    context.getClaimReason(),
                    document.getOcrText(),
                    Map.of(),
                    answerList);
            document.setValidationResult(result);
            document.setStatus("COMPLETED");
            log.info("claimId={} documentId={} agent=ValidationAgent status=COMPLETE",
                    claimId, document.getDocumentId());
        }

        String finalStatus = computeStatus(context.getDocuments());
        context.setStatus(finalStatus);

        // ONE claim-level decision, computed once all documents (and their validations,
        // where applicable) are in - never per document.
        ClaimDecision decision = claimDecisionAgent.decide(claimId, context.getDocuments());

        claimRepository.save(toClaim(context, decision));

        log.info("claimId={} agent=ClaimOrchestrator status=COMPLETE finalStatus={} verdict={}",
                claimId, finalStatus, decision.getVerdict());

        return ClaimResult.builder()
                .claimId(claimId)
                .status(finalStatus)
                .documents(context.getDocuments())
                .applicablePolicyClauseSection(context.getPolicyClause() != null
                        ? context.getPolicyClause().getSection() : null)
                .finalDecision(decision)
                .build();
    }

    /**
     * COMPLETED: every document reached a normal disposition (valid+relevant+validated,
     * OR an expected/deterministic rejection like an invalid file or an unrelated document).
     * PARTIALLY_COMPLETED: at least one document hit an unexpected processing error (OCR_FAILED)
     * but at least one other document still completed normally.
     * FAILED: no documents were submitted, or every single one hit OCR_FAILED.
     */
    private String computeStatus(List<DocumentResult> documents) {
        if (documents.isEmpty()) {
            return "FAILED";
        }
        boolean anyOcrFailure = documents.stream().anyMatch(d -> d.getErrors() != null && d.getErrors().contains("OCR_FAILED"));
        boolean allOcrFailure = documents.stream().allMatch(d -> d.getErrors() != null && d.getErrors().contains("OCR_FAILED"));
        if (allOcrFailure) {
            return "FAILED";
        }
        return anyOcrFailure ? "PARTIALLY_COMPLETED" : "COMPLETED";
    }

    private List<ClaimAnswer> toClaimAnswers(Map<String, Object> answers) {
        if (answers == null) {
            return List.of();
        }
        return answers.entrySet().stream()
                .map(e -> new ClaimAnswer(e.getKey(), e.getKey(), String.valueOf(e.getValue())))
                .toList();
    }

    private Claim toClaim(ClaimContext context, ClaimDecision decision) {
        List<ClaimDocument> documents = context.getDocuments().stream().map(this::toClaimDocument).toList();
        Instant now = Instant.now();
        // decision.verdict is one of AUTO_APPROVED/AUTO_REJECTED/UNDER_REVIEW - the exact
        // same names as ClaimStatus, so the existing adjuster ReviewController.approve/reject
        // can act on a claim this pipeline routed to UNDER_REVIEW exactly like a wizard claim.
        ClaimStatus status = resolveStatus(decision.getVerdict());
        return Claim.builder()
                .claimId(context.getClaimId())
                .claimType(context.getClaimType())
                .claimReason(context.getClaimReason())
                .answers(toClaimAnswers(context.getAnswers()))
                .documents(documents)
                .processingStatus(context.getStatus())
                .finalDecision(decision)
                .status(status)
                .statusHistory(List.of(
                        new StatusChange(ClaimStatus.SUBMITTED, now, "Claim submitted", "SYSTEM"),
                        new StatusChange(status, now, decision.getReasoning(), "SYSTEM")))
                .createdAt(now)
                .build();
    }

    private ClaimStatus resolveStatus(String verdict) {
        try {
            return ClaimStatus.valueOf(verdict);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("Unrecognized ClaimDecisionAgent verdict '{}' - defaulting to UNDER_REVIEW", verdict);
            return ClaimStatus.UNDER_REVIEW;
        }
    }

    private ClaimDocument toClaimDocument(DocumentResult d) {
        ValidationResult v = d.getValidationResult();
        return ClaimDocument.builder()
                .fileName(d.getFileName())
                .documentId(d.getDocumentId())
                .fileRef(d.getFileRef())
                .valid(d.isValid())
                .errors(d.getErrors())
                .ocrText(d.getOcrText())
                .documentType(d.getDocumentType())
                .relevant(d.isRelevant())
                .relevanceConfidence(d.getRelevanceConfidence())
                .similarityScore(d.getSimilarityScore())
                .relevanceReason(d.getRelevanceReason())
                .decisionSource(d.getDecisionSource())
                .status(d.getStatus())
                .flags(v != null ? v.flags() : null)
                .clauseSatisfied(v != null ? "APPROVE".equals(v.decision()) : null)
                .validationConfidence(v != null ? v.confidence() : null)
                .validationReason(v != null ? v.explanation() : null)
                .build();
    }
}
