package com.nextgen.claims.agent;

import com.nextgen.claims.dto.ClaimDecision;
import com.nextgen.claims.dto.DocumentResult;
import com.nextgen.claims.service.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The ONE final, claim-level decision: AUTO_APPROVED / AUTO_REJECTED / UNDER_REVIEW.
 * Runs once per claim, after every document has been through DocumentAgent and
 * (if valid+relevant) ValidationAgent - never per document.
 *
 * The confidence value and the threshold rule are computed deterministically
 * in Java and handed to Ollama as a constraint, exactly like the existing
 * FinalJudgeAgent pattern (agent/FinalJudgeAgent.java) - Ollama picks the
 * verdict and writes the human-readable reasoning, but it isn't reasoning
 * from a blank slate, and the confidenceScore it returns is always
 * overwritten with the precomputed value so the number can't drift from
 * what the threshold check actually used.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimDecisionAgent {

    private final OllamaService ollamaService;

    @Value("${claims.decision.confidence-threshold:0.65}")
    private double confidenceThreshold;

    public ClaimDecision decide(String claimId, List<DocumentResult> documents) {
        List<DocumentResult> validated = documents.stream()
                .filter(d -> d.getValidationResult() != null)
                .toList();

        if (validated.isEmpty()) {
            log.info("claimId={} agent=ClaimDecisionAgent verdict=UNDER_REVIEW reason=no_validated_documents", claimId);
            return ClaimDecision.builder()
                    .verdict("UNDER_REVIEW")
                    .confidenceScore(0.0)
                    .reasoning("No submitted document was both valid and relevant, so there is no evidence to " +
                            "approve or reject this claim automatically.")
                    .recommendedAction("Route to a human adjuster for manual document review.")
                    .keyReasons(List.of("No document reached policy validation"))
                    .build();
        }

        double avgConfidence = validated.stream()
                .mapToDouble(d -> d.getValidationResult().confidence())
                .average()
                .orElse(0.0);

        String findingsBlock = validated.stream()
                .map(d -> String.format(
                        "Document: %s (%s)%n  decision=%s confidence=%.2f%n  flags=%s%n  explanation=%s",
                        d.getFileName(), d.getDocumentType(),
                        d.getValidationResult().decision(), d.getValidationResult().confidence(),
                        d.getValidationResult().flags(), d.getValidationResult().explanation()))
                .collect(Collectors.joining("\n\n"));

        String prompt = """
                You are the final claims adjudication judge for one insurance claim. Each submitted
                document has already been individually checked against the applicable policy clause
                and the customer's answers. Synthesize those per-document findings into ONE final
                decision for the whole claim. Do not re-examine the documents yourself - reason only
                from the findings below.

                PER-DOCUMENT FINDINGS:
                %s

                Pre-computed average confidence across all validated documents: %.2f
                Decision threshold: %.2f

                Decision rules:
                - AUTO_APPROVED: every document's decision is APPROVE, no "mismatch:*" flag appears on
                  any document, and the average confidence is at/above the threshold.
                - AUTO_REJECTED: at least one document's decision is REJECT (a clear policy clause
                  violation) and its confidence is at/above the threshold.
                - UNDER_REVIEW: anything borderline - any document decision is INVESTIGATE, any
                  "mismatch:*" flag exists, or the average confidence is below the threshold.

                Return valid JSON with these exact fields:
                - verdict: "AUTO_APPROVED" | "AUTO_REJECTED" | "UNDER_REVIEW"
                - confidenceScore: use the pre-computed average confidence value above exactly
                - reasoning: 2-3 sentence narrative for a claims handler
                - recommendedAction: one actionable sentence
                - keyReasons: array of 3-5 short bullet point strings
                """.formatted(findingsBlock, avgConfidence, confidenceThreshold);

        try {
            ClaimDecision raw = ollamaService.generateStructured(prompt, ClaimDecision.class);
            ClaimDecision decision = ClaimDecision.builder()
                    .verdict(raw.getVerdict())
                    .confidenceScore(avgConfidence)
                    .reasoning(raw.getReasoning())
                    .recommendedAction(raw.getRecommendedAction())
                    .keyReasons(raw.getKeyReasons())
                    .build();
            log.info("claimId={} agent=ClaimDecisionAgent verdict={} confidence={}",
                    claimId, decision.getVerdict(), avgConfidence);
            return decision;
        } catch (OllamaService.OllamaException e) {
            // Never crash the claim over an Ollama outage - fall back to the deterministic
            // threshold check alone and route to a human instead of guessing a verdict.
            log.warn("claimId={} agent=ClaimDecisionAgent status=OLLAMA_FALLBACK errorCode={}",
                    claimId, e.getErrorCode());
            return ClaimDecision.builder()
                    .verdict("UNDER_REVIEW")
                    .confidenceScore(avgConfidence)
                    .reasoning("Automated adjudication was unavailable (" + e.getErrorCode() + "); routed for manual review.")
                    .recommendedAction("Route to a human adjuster.")
                    .keyReasons(List.of("Ollama unavailable during final decision"))
                    .build();
        }
    }
}
