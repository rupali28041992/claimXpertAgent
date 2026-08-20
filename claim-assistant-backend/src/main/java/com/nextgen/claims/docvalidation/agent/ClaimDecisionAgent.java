package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.config.DocValidationProperties;
import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.ClaimDecisionResult;
import com.nextgen.claims.docvalidation.model.ClaimDecisionStatus;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.service.OllamaService;
import com.nextgen.claims.docvalidation.service.OllamaServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The ONLY LLM call in the whole submit flow - one Ollama call per claim.
 * Relevance judgment and per-document clause-satisfaction checking used to
 * be separate LLM calls (DocumentRelevanceAgent, DocumentValidationAgent);
 * both are folded into this single prompt now, for latency - fewer
 * sequential round trips to a local model. Called once by
 * ClaimOrchestrator, after OCR has run for every uploaded file.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimDecisionAgent {

    private final OllamaService ollamaService;
    private final DocValidationProperties properties;

    public ClaimDecisionResult decide(ClaimContext context) {
        List<DocumentResult> validDocuments = context.getDocuments().stream()
                .filter(DocumentResult::isValid)
                .toList();
        List<PolicyClause> clauses = context.getPolicyClauses() == null ? List.of() : context.getPolicyClauses();

        if (validDocuments.isEmpty() || clauses.isEmpty()) {
            log.info("[ClaimDecisionAgent] claim={} short-circuit to MANUAL_REVIEW: validDocuments={} clauses={}",
                    context.getClaimId(), validDocuments.size(), clauses.size());
            return logAndReturn(context, ClaimDecisionResult.builder()
                    .decision(ClaimDecisionStatus.MANUAL_REVIEW)
                    .conditions(List.of())
                    .matchedClauses(List.of())
                    .confidence(0.0)
                    .reason(validDocuments.isEmpty()
                            ? "No valid documents were submitted for this claim."
                            : "No applicable policy clause could be retrieved for this claim.")
                    .build());
        }

        String prompt = buildPrompt(context, validDocuments, clauses);

        try {
            ClaimDecisionResult result = ollamaService.generateStructured(prompt, ClaimDecisionResult.class);
            return logAndReturn(context, result);
        } catch (OllamaServiceException e) {
            log.warn("[ClaimDecisionAgent] claim={} decision failed ({}), defaulting to MANUAL_REVIEW",
                    context.getClaimId(), e.getCode());
            return logAndReturn(context, ClaimDecisionResult.builder()
                    .decision(ClaimDecisionStatus.MANUAL_REVIEW)
                    .conditions(List.of())
                    .matchedClauses(List.of())
                    .confidence(0.0)
                    .reason("Decision could not be completed: " + e.getCode())
                    .build());
        }
    }

    private String buildPrompt(ClaimContext context, List<DocumentResult> validDocuments, List<PolicyClause> clauses) {
        int maxOcrChars = properties.getDecision().getMaxOcrCharsPerDoc();

        StringBuilder documentsBlock = new StringBuilder();
        for (DocumentResult document : validDocuments) {
            String ocrText = document.getOcrText() == null ? "" : document.getOcrText();
            if (ocrText.length() > maxOcrChars) {
                ocrText = ocrText.substring(0, maxOcrChars) + "...(truncated)";
            }
            documentsBlock.append("Document: ").append(document.getFileName())
                    .append("\nOCR Text:\n").append(ocrText)
                    .append("\n---\n");
        }

        StringBuilder clausesBlock = new StringBuilder();
        for (PolicyClause clause : clauses) {
            clausesBlock.append("Section: ").append(clause.getClaimReason())
                    .append("\n").append(clause.getClauseText()).append("\n---\n");
        }

        return """
                You are the final Claim Decision Agent for an insurance claim processing system.

                No separate relevance or per-document validation step has run before
                this. As part of this single decision you must judge, for each
                submitted document: whether it is actually relevant to this claim, and
                whether it satisfies the applicable retrieved policy clause(s). Use
                ONLY the evidence supplied below - the documents' OCR text and the
                retrieved policy clauses.

                Decide exactly one of: APPROVED, REJECTED, MANUAL_REVIEW.
                - If a document is not relevant to this claim type/reason, disregard
                  it as evidence rather than treating it as support for the claim.
                - Use MANUAL_REVIEW whenever the evidence is insufficient (e.g. no
                  relevant document at all, or documents that don't clearly relate to
                  this claim), the documents conflict with each other or with the
                  user's answers in a way that isn't clear-cut, or no clause clearly
                  applies. Do not guess.
                - Use REJECTED only when a specific retrieved clause clearly
                  disqualifies the claim (e.g. waiting period not met, permanent
                  exclusion, mandatory document missing) - cite that clause in "reason".
                - Use APPROVED only when at least one relevant document satisfies the
                  applicable clause(s) and is consistent with the user's answers. List
                  any binding conditions found in the clause text (co-payment
                  percentages, sub-limits, restoration rules, etc.) in "conditions".

                CLAIM TYPE:
                %s

                CLAIM REASON:
                %s

                USER ANSWERS:
                %s

                SUBMITTED DOCUMENTS:
                %s

                RETRIEVED POLICY CLAUSES:
                %s

                Return ONLY valid JSON:
                {"decision": "APPROVED", "conditions": [], "matchedClauses": [], "confidence": 0.9, "reason": "..."}

                Rules:
                - Do not invent facts not present in the evidence above.
                - "matchedClauses" must only list section titles that were actually supplied above.
                - Do not output markdown.
                - Return JSON only.
                """.formatted(context.getClaimType(), context.getClaimReason(), context.getAnswers(),
                documentsBlock, clausesBlock);
    }

    private ClaimDecisionResult logAndReturn(ClaimContext context, ClaimDecisionResult result) {
        log.info("[ClaimDecisionAgent] claim={} decision={} confidence={} conditions={} matchedClauses={} reason={}",
                context.getClaimId(), result.getDecision(), result.getConfidence(),
                result.getConditions(), result.getMatchedClauses(), result.getReason());
        return result;
    }
}
