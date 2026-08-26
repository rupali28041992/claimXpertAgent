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
                You are an expert insurance claims adjudicator. Produce a single JSON decision.

                ── VERIFIED DOCUMENT FLAGS (pre-computed, authoritative — trust these exactly) ──
                  DISCHARGE_SUMMARY_PRESENT : %s   ← if true, a valid discharge summary was verified
                  BILL_PRESENT              : %s   ← if true, a valid hospital bill was verified

                ── CLAIM DETAILS ──────────────────────────────────────────────
                  Claim type   : %s
                  Claim reason : %s
                  Diagnosis    : %s

                ── CLAIMANT QUESTIONNAIRE ANSWERS ─────────────────────────────
                %s

                ── SUBMITTED DOCUMENT EVIDENCE ────────────────────────────────
                %s

                ── APPLICABLE POLICY CLAUSES ──────────────────────────────────
                %s

                DECISION RULES (apply in order):
                1. REJECTED — only if a clause explicitly EXCLUDES the diagnosis or event type. \
                   Missing documents are never a reason to reject.
                2. APPROVED — if at least one coverage clause is satisfied by the evidence and no \
                   exclusion applies. Use the DISCHARGE_SUMMARY_PRESENT and BILL_PRESENT flags as \
                   the authoritative source for whether those documents exist.
                3. MANUAL_REVIEW — if evidence is incomplete or ambiguous and no exclusion applies.

                CRITICAL RULES:
                - If DISCHARGE_SUMMARY_PRESENT is true, you MUST NOT say the discharge summary \
                  is missing or not found. It is verified present.
                - If BILL_PRESENT is true, you MUST NOT say the bill is missing.
                - A discharge summary alone (without a bill) can satisfy hospitalisation clauses \
                  if the clause does not explicitly require a bill.
                - Base your decision on the clauses and evidence above only.

                OUTPUT FORMAT (respond with ONLY valid JSON, no extra text):
                {
                  "decision"      : "APPROVED" | "REJECTED" | "MANUAL_REVIEW",
                  "reason"        : "2-4 sentences referencing specific clauses and evidence",
                  "keyFindings"   : ["finding 1", "finding 2", ...],
                  "conditions"    : ["any condition or caveat, e.g. subject to verification"],
                  "matchedClauses": ["exact clause name from the policy clauses above"],
                  "confidence"    : 0.0-1.0
                }

                EXAMPLE — APPROVED (discharge summary present, no bill required by clause):
                {"decision":"APPROVED","reason":"The discharge summary confirms Acute Appendicitis. The In-patient Hospitalisation clause covers surgical treatment requiring 24+ hour admission and does not mandate a separate hospital bill. No exclusion clause applies to Appendicitis.","keyFindings":["Discharge summary present and verified — diagnosis: Acute Appendicitis","Treatment: Laparoscopic Appendectomy","Clause 'In-patient Hospitalisation' matched: covers surgical in-patient procedures","No exclusion clause applies to this diagnosis"],"conditions":[],"matchedClauses":["In-patient Hospitalisation"],"confidence":0.91}

                EXAMPLE — REJECTED (diagnosis explicitly excluded by policy):
                {"decision":"REJECTED","reason":"The claim is for dental treatment. Clause 'Exclusions — Dental' explicitly excludes routine and surgical dental procedures. This exclusion applies regardless of documents submitted.","keyFindings":["Diagnosis: dental treatment","Clause 'Exclusions — Dental' directly excludes this diagnosis","Rejection is based on policy exclusion, not missing documents"],"conditions":[],"matchedClauses":["Exclusions — Dental"],"confidence":0.98}

                EXAMPLE — MANUAL_REVIEW (clause requires bill, bill not present):
                {"decision":"MANUAL_REVIEW","reason":"The In-patient Hospitalisation clause requires an itemised hospital bill for claims above Rs.10,000. The discharge summary is present but no bill was submitted. The claim cannot be approved without the bill.","keyFindings":["Discharge summary present and verified — diagnosis: Typhoid Fever","Bill: NOT PRESENT — required by clause for amounts above Rs.10,000","No exclusion applies to the stated diagnosis","Manual reviewer should request itemised hospital bill"],"conditions":["Itemised hospital bill required before approval"],"matchedClauses":["In-patient Hospitalisation"],"confidence":0.5}

                Now produce the JSON decision for the claim above:
                """
                .formatted(
                        hasDischarge,
                        hasBill,
                        context.getClaimType(),
                        context.getClaimReason(),
                        diagnosis,
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
            appendIfPresent(sb, "DocType    ", e.getDocumentType());
            appendIfPresent(sb, "PatientName", e.getPatientName());
            appendIfPresent(sb, "Hospital   ", e.getHospitalName());
            appendIfPresent(sb, "Diagnosis  ", e.getDiagnosis());
            appendIfPresent(sb, "Treatment  ", e.getTreatment());
            appendIfPresent(sb, "BillAmount ", e.getBillAmount());
            appendIfPresent(sb, "Admission  ", e.getAdmissionDate());
            appendIfPresent(sb, "Discharge  ", e.getDischargeDate());
            appendIfPresent(sb, "PolicyNo   ", e.getPolicyNumber());
            appendIfPresent(sb, "ClaimNo    ", e.getClaimNumber());
            sb.append("\n");
        }
        return sb.isEmpty() ? "  (no evidence extracted)" : sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("  ").append(label).append(": ").append(value).append("\n");
        }
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