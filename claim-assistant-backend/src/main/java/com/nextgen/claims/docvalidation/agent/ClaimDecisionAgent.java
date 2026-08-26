package com.nextgen.claims.docvalidation.agent;

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

            if (allDocumentsWrongOrIrrelevant(context.getDocuments())) {

                log.info(
                        "[ClaimDecisionAgent] claim={} all documents wrong/irrelevant — rejecting",
                        context.getClaimId()
                );

                return logAndReturn(
                        context,
                        rejected(
                                "None of the uploaded documents match this claim — "
                                        + "they were either irrelevant to the claim type or "
                                        + "declared under the wrong document category."
                        )
                );
            }

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

        log.debug(
                "[ClaimDecisionAgent] claim={} FULL PROMPT SENT TO OLLAMA:\n{}",
                context.getClaimId(),
                prompt
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
                    manualReviewAiError(
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

        String evidenceSummary = buildEvidenceSummary(documents);

        String clausesSummary = clauses.stream()
                .map(c -> "  [" + c.getClaimReason() + "]: " + c.getClauseText())
                .reduce("", (a, b) -> a + "\n" + b);

        String answersSummary = buildAnswersSummary(context);

        // Explicit boolean flags — small models miss presence/absence when buried in detail
        boolean hasDischarge = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                "DISCHARGE_SUMMARY".equalsIgnoreCase(d.getEvidence().getDocumentType()));
        boolean hasBill = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                d.getEvidence().getBillAmount() != null &&
                !d.getEvidence().getBillAmount().isBlank());
        String diagnosis = documents.stream()
                .filter(d -> d.getEvidence() != null && d.getEvidence().getDiagnosis() != null)
                .map(d -> d.getEvidence().getDiagnosis())
                .findFirst().orElse("not identified");

        return """
                /no_think
                You are an expert insurance claims adjudicator. Read ALL the information below carefully \
                and then make your own independent decision.

                ── CLAIM DETAILS ──────────────────────────────────────────────
                  Claim type                : %s
                  Claim reason              : %s
                  Diagnosis found           : %s
                  DISCHARGE_SUMMARY_PRESENT : %s
                  BILL_PRESENT              : %s

                ── CLAIMANT QUESTIONNAIRE ANSWERS ─────────────────────────────
                %s

                ── SUBMITTED DOCUMENT EVIDENCE (full detail) ──────────────────
                %s

                ── APPLICABLE POLICY CLAUSES ──────────────────────────────────
                %s

                YOUR TASK:
                1. Read each policy clause and determine whether it covers or excludes the claim.
                2. Check whether the submitted documents satisfy the requirements stated in the clauses \
                   (e.g. discharge summary, itemised bill, FIR, death certificate — whatever the clause demands).
                3. If any clause explicitly excludes the diagnosis or event → decide REJECTED and cite it.
                4. If the evidence satisfies at least one coverage clause and no exclusion applies → decide APPROVED.
                5. If evidence is incomplete or ambiguous and no exclusion applies → decide MANUAL_REVIEW.

                Do NOT use hardcoded rules. Base your decision entirely on the clauses and evidence above.

                OUTPUT FORMAT (respond with ONLY valid JSON, no extra text):
                {
                  "decision"      : "APPROVED" | "REJECTED" | "MANUAL_REVIEW",
                  "reason"        : "2-4 sentences referencing specific clauses and evidence",
                  "keyFindings"   : ["finding 1", "finding 2", ...],
                  "conditions"    : ["any condition or caveat, e.g. subject to verification"],
                  "matchedClauses": ["exact clause name from the policy clauses above"],
                  "confidence"    : 0.0-1.0
                }

                EXAMPLES:

                APPROVED:
                {"decision":"APPROVED","reason":"The discharge summary confirms Acute Appendicitis (not listed as an excluded condition in any clause) and an itemised hospital bill of Rs.45,000 is present. The In-patient Hospitalisation clause explicitly covers surgical treatment requiring 24+ hour admission. All document requirements stated in the clause are satisfied.","keyFindings":["Discharge summary present — diagnosis: Acute Appendicitis","Hospital bill present — amount: Rs.45,000","Clause \\"In-patient Hospitalisation\\" matched: covers surgical procedures","No exclusion clause applies to Appendicitis","Admission: 10-Jan-2024, Discharge: 15-Jan-2024 (5 days — satisfies 24h minimum)"],"conditions":[],"matchedClauses":["In-patient Hospitalisation"],"confidence":0.94}

                REJECTED:
                {"decision":"REJECTED","reason":"The claim is for dental treatment. The policy clause \\"Exclusions — Dental\\" explicitly states that routine and surgical dental procedures are not covered under this policy. This exclusion applies regardless of whether supporting documents are present.","keyFindings":["Diagnosis: dental treatment","Clause \\"Exclusions — Dental\\" directly excludes this diagnosis","No coverage clause overrides this exclusion","Rejection is based on policy terms, not missing documents"],"conditions":["Diagnosis falls under an explicit exclusion"],"matchedClauses":["Exclusions — Dental"],"confidence":0.98}

                MANUAL_REVIEW:
                {"decision":"MANUAL_REVIEW","reason":"The In-patient Hospitalisation clause requires both a discharge summary and an itemised hospital bill. A hospital bill was submitted but no discharge summary was found in the uploaded documents. The claim cannot be approved or rejected without the missing document.","keyFindings":["Hospital bill present — amount: Rs.28,000","Discharge summary: NOT FOUND","Clause \\"In-patient Hospitalisation\\" requires discharge summary for approval","No exclusion applies to the stated diagnosis","Manual reviewer should request discharge summary from claimant"],"conditions":["Discharge summary required before approval decision"],"matchedClauses":["In-patient Hospitalisation"],"confidence":0.5}

                Now produce the JSON decision for the claim details and evidence above:
                """
                .formatted(
                        context.getClaimType(),
                        context.getClaimReason(),
                        diagnosis,
                        hasDischarge,
                        hasBill,
                        answersSummary,
                        evidenceSummary,
                        clausesSummary
                );
    }

    private String buildEvidenceSummary(List<DocumentResult> documents) {
        StringBuilder sb = new StringBuilder();
        for (DocumentResult doc : documents) {
            var e = doc.getEvidence();
            if (e == null) continue;
            sb.append("  File       : ").append(doc.getFileName()).append("\n");
            sb.append("  DocType    : ").append(e.getDocumentType()).append("\n");
            sb.append("  PatientName: ").append(e.getPatientName()).append("\n");
            sb.append("  Hospital   : ").append(e.getHospitalName()).append("\n");
            sb.append("  Diagnosis  : ").append(e.getDiagnosis()).append("\n");
            sb.append("  Treatment  : ").append(e.getTreatment()).append("\n");
            sb.append("  BillAmount : ").append(e.getBillAmount()).append("\n");
            sb.append("  Admission  : ").append(e.getAdmissionDate()).append("\n");
            sb.append("  Discharge  : ").append(e.getDischargeDate()).append("\n");
            sb.append("  PolicyNo   : ").append(e.getPolicyNumber()).append("\n");
            sb.append("  ClaimNo    : ").append(e.getClaimNumber()).append("\n");
            sb.append("\n");
        }
        return sb.isEmpty() ? "  (no evidence extracted)" : sb.toString();
    }

    private String buildAnswersSummary(ClaimContext context) {
        if (context.getAnswers() == null || context.getAnswers().isEmpty()) {
            return "  (no questionnaire answers provided)";
        }
        StringBuilder sb = new StringBuilder();
        context.getAnswers().forEach((key, value) ->
                sb.append("  ").append(key).append(": ").append(value).append("\n"));
        return sb.toString();
    }

    /**
     * True only when EVERY uploaded document failed because it was the wrong
     * document for this claim (content irrelevant to the claim type, or
     * declared under the wrong category) — never for technical upload
     * problems (OCR failure, corrupt/oversized file), which stay
     * MANUAL_REVIEW since those could be an honest upload mistake rather
     * than an invalid claim.
     */
    private boolean allDocumentsWrongOrIrrelevant(List<DocumentResult> documents) {
        if (documents == null || documents.isEmpty()) {
            return false;
        }
        return documents.stream().allMatch(this::isWrongDocumentError);
    }

    private boolean isWrongDocumentError(DocumentResult document) {
        List<String> errors = document.getErrors();
        if (errors == null || errors.isEmpty()) {
            return false;
        }
        return errors.stream().allMatch(err ->
                "DOCUMENT_NOT_RELEVANT".equals(err) || err.startsWith("Document mislabeled"));
    }

    private ClaimDecisionResult rejected(String reason) {
        return ClaimDecisionResult.builder()
                .decision(ClaimDecisionStatus.REJECTED)
                .conditions(List.of())
                .matchedClauses(List.of())
                .confidence(1.0)
                .reason(reason)
                .build();
    }

    private ClaimDecisionResult manualReview(String reason) {
        return ClaimDecisionResult.builder()
                .decision(ClaimDecisionStatus.MANUAL_REVIEW)
                .conditions(List.of())
                .matchedClauses(List.of())
                .confidence(0.0)
                .reason(reason)
                .build();
    }

    private ClaimDecisionResult manualReviewAiError(String reason) {
        return ClaimDecisionResult.builder()
                .decision(ClaimDecisionStatus.MANUAL_REVIEW)
                .conditions(List.of())
                .matchedClauses(List.of())
                .confidence(0.0)
                .reason(reason)
                .aiError(true)
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