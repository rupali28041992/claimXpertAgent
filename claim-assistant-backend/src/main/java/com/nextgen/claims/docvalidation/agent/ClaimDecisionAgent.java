package com.nextgen.claims.docvalidation.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimDecisionAgent {

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;

    public ClaimDecisionResult decide(ClaimContext context) {

        List<DocumentResult> validDocuments =
                context.getDocuments()
                        .stream()
                        .filter(DocumentResult::isValid)
                        .filter(d -> d.getEvidence() != null)
                        .toList();

        List<PolicyClause> clauses =
                context.getPolicyClauses() == null
                        ? List.of()
                        : context.getPolicyClauses();

        // ---------------------------------------------------------
        // Short circuit
        // ---------------------------------------------------------

        if (validDocuments.isEmpty()) {

            log.info(
                    "[ClaimDecisionAgent] claim={} no valid evidence",
                    context.getClaimId()
            );

            return logAndReturn(
                    context,
                    manualReview(
                            "No valid relevant document evidence was extracted."
                    )
            );
        }

        if (clauses.isEmpty()) {

            log.info(
                    "[ClaimDecisionAgent] claim={} no policy clauses",
                    context.getClaimId()
            );

            return logAndReturn(
                    context,
                    manualReview(
                            "No applicable policy clause was retrieved."
                    )
            );
        }

        // ---------------------------------------------------------
        // Build SMALL prompt
        // ---------------------------------------------------------

        String prompt =
                buildPrompt(
                        context,
                        validDocuments,
                        clauses
                );

        log.info(
                "[ClaimDecisionAgent] claim={} promptChars={} documents={} clauses={}",
                context.getClaimId(),
                prompt.length(),
                validDocuments.size(),
                clauses.size()
        );

        try {

            ClaimDecisionResult result =
                    ollamaService.generateStructured(
                            prompt,
                            ClaimDecisionResult.class
                    );

            if (result == null) {

                return logAndReturn(
                        context,
                        manualReview(
                                "Ollama returned no decision."
                        )
                );
            }

            return logAndReturn(context, result);

        } catch (OllamaServiceException e) {

            log.warn(
                    "[ClaimDecisionAgent] claim={} decision failed code={}",
                    context.getClaimId(),
                    e.getCode()
            );

            return logAndReturn(
                    context,
                    manualReview(
                            "Decision could not be completed: "
                                    + e.getCode()
                    )
            );
        }
    }

    private String buildPrompt(
            ClaimContext context,
            List<DocumentResult> documents,
            List<PolicyClause> clauses) {

        // Summarise evidence into one short line per document to keep the prompt small.
        String evidenceSummary = buildEvidenceSummary(documents);

        // Only the clause names + text — no embeddings.
        String clausesSummary = clauses.stream()
                .map(c -> "- " + c.getClaimReason() + ": " + c.getClauseText())
                .reduce("", (a, b) -> a + "\n" + b);

        // Explicit exclusions extracted from policy for the model to check against.
        String exclusionKeywords =
                "cosmetic surgery, dental treatment, self-inflicted injury, " +
                "AIDS/HIV, congenital disease, fertility treatment, obesity surgery, " +
                "war injury, voluntary sterilisation";

        boolean hasDischarge = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                "DISCHARGE_SUMMARY".equals(d.getEvidence().getDocumentType()));

        boolean hasBill = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                d.getEvidence().getBillAmount() != null &&
                !d.getEvidence().getBillAmount().isBlank());

        String diagnosis = documents.stream()
                .filter(d -> d.getEvidence() != null && d.getEvidence().getDiagnosis() != null)
                .map(d -> d.getEvidence().getDiagnosis())
                .findFirst().orElse("unknown");

        return """
                /no_think
                You are an insurance claim approval engine. Follow these exact rules:

                DECISION RULES (apply in order):
                1. If the diagnosis matches any item in EXCLUSIONS → output REJECTED.
                2. If DISCHARGE_SUMMARY_PRESENT=true AND BILL_PRESENT=true → output APPROVED.
                3. Otherwise → output MANUAL_REVIEW.

                EXCLUSIONS (diagnoses NOT covered):
                %s

                INPUTS:
                Claim type          : %s
                Claim reason        : %s
                Diagnosis found     : %s
                DISCHARGE_SUMMARY_PRESENT: %s
                BILL_PRESENT        : %s

                DOCUMENT EVIDENCE SUMMARY:
                %s

                POLICY CLAUSES (for reference only — do not invent exclusions):
                %s

                EXAMPLE — when discharge summary and bill are present and diagnosis is not excluded:
                {"decision":"APPROVED","conditions":[],"matchedClauses":["In-patient Hospitalisation"],"confidence":0.92,"reason":"Discharge summary and hospital bill present; diagnosis not excluded."}

                Now output ONLY valid JSON matching the example format:
                """
                .formatted(
                        exclusionKeywords,
                        context.getClaimType(),
                        context.getClaimReason(),
                        diagnosis,
                        hasDischarge,
                        hasBill,
                        evidenceSummary,
                        clausesSummary
                );
    }

    private String buildEvidenceSummary(List<DocumentResult> documents) {
        StringBuilder sb = new StringBuilder();
        for (DocumentResult doc : documents) {
            var e = doc.getEvidence();
            if (e == null) continue;
            sb.append("  [").append(e.getDocumentType()).append("] ")
              .append("diagnosis=").append(e.getDiagnosis()).append(", ")
              .append("treatment=").append(e.getTreatment()).append(", ")
              .append("billAmount=").append(e.getBillAmount()).append(", ")
              .append("admissionDate=").append(e.getAdmissionDate()).append(", ")
              .append("dischargeDate=").append(e.getDischargeDate()).append("\n");
        }
        return sb.toString();
    }

    private record ClauseProjection(String claimType, String claimReason, String clauseText) {}

    private ClaimDecisionResult manualReview(String reason) {

        return ClaimDecisionResult.builder()
                .decision(ClaimDecisionStatus.MANUAL_REVIEW)
                .conditions(List.of())
                .matchedClauses(List.of())
                .confidence(0.0)
                .reason(reason)
                .build();
    }

    private ClaimDecisionResult logAndReturn(
            ClaimContext context,
            ClaimDecisionResult result) {

        log.info(
                "[ClaimDecisionAgent] claim={} decision={} confidence={} reason={}",
                context.getClaimId(),
                result.getDecision(),
                result.getConfidence(),
                result.getReason()
        );

        return result;
    }
}