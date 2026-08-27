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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimDecisionAgent {

    private static final Set<String> GENERIC_HOSPITAL_WORDS = Set.of(
            "hospital", "hospitals", "clinic", "clinics", "medical", "center",
            "centre", "care", "health", "healthcare", "general", "city",
            "the", "and", "nursing", "home");

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;

    public ClaimDecisionResult decide(ClaimContext context) {

        List<DocumentResult> allDocuments =
                context.getDocuments() == null ? List.of() : context.getDocuments();

        // ---------------------------------------------------------
        // Deterministic fraud/integrity checks — run BEFORE any AI
        // call. A mislabeled document or a hospital name that does
        // not match the claimant's declaration is a hard rejection
        // signal, not something that needs an AI judgement call.
        // ---------------------------------------------------------

        Optional<DocumentResult> mislabeled = allDocuments.stream()
                .filter(d -> !d.isValid() && d.getErrors() != null)
                .filter(d -> d.getErrors().stream()
                        .anyMatch(e -> e.startsWith("Document mislabeled")))
                .findFirst();

        if (mislabeled.isPresent()) {
            DocumentResult doc = mislabeled.get();
            log.info(
                    "[ClaimDecisionAgent] claim={} rejecting — mislabeled document file={}",
                    context.getClaimId(),
                    doc.getFileName()
            );
            return logAndReturn(context, rejected(
                    "Document '" + doc.getFileName() + "' does not match its declared type: "
                            + String.join("; ", doc.getErrors())));
        }

        List<DocumentResult> validDocuments =
                allDocuments
                        .stream()
                        .filter(DocumentResult::isValid)
                        .filter(d -> d.getEvidence() != null)
                        .toList();

        Optional<String> hospitalMismatch = checkHospitalNameMismatch(context, validDocuments);

        if (hospitalMismatch.isPresent()) {
            log.info(
                    "[ClaimDecisionAgent] claim={} rejecting — hospital name mismatch",
                    context.getClaimId()
            );
            return logAndReturn(context, rejected(hospitalMismatch.get()));
        }

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

    /**
     * Compares the claimant's declared hospital_name answer against the
     * hospitalName extracted from every valid document. Returns a rejection
     * reason when neither side shares a significant (non-generic) word with
     * the other — e.g. "Random Hospital" declared vs "Apollo Hospital"
     * extracted. Returns empty when there is nothing reliable to compare
     * (no declared name, no extracted name, or only generic words like
     * "Hospital"/"Clinic" on either side) to avoid false-positive rejections.
     */
    private Optional<String> checkHospitalNameMismatch(
            ClaimContext context,
            List<DocumentResult> validDocuments) {

        Object declared = context.getAnswers() == null
                ? null
                : context.getAnswers().get("hospital_name");

        if (declared == null || declared.toString().isBlank()) {
            return Optional.empty();
        }

        List<String> extractedNames = validDocuments.stream()
                .map(d -> d.getEvidence().getHospitalName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        if (extractedNames.isEmpty()) {
            return Optional.empty();
        }

        String declaredName = declared.toString();
        boolean anyMatch = extractedNames.stream()
                .anyMatch(name -> hospitalNamesMatch(declaredName, name));

        if (anyMatch) {
            return Optional.empty();
        }

        return Optional.of(
                "Declared hospital '" + declaredName + "' does not match the hospital named "
                        + "in the submitted documents (" + String.join(", ", extractedNames) + ").");
    }

    private boolean hospitalNamesMatch(String declared, String extracted) {
        Set<String> declaredTokens = significantTokens(declared);
        Set<String> extractedTokens = significantTokens(extracted);

        // Nothing significant to compare (e.g. both sides just say "Hospital") —
        // don't reject on a comparison that can't actually distinguish names.
        if (declaredTokens.isEmpty() || extractedTokens.isEmpty()) {
            return true;
        }

        return declaredTokens.stream().anyMatch(extractedTokens::contains);
    }

    private Set<String> significantTokens(String value) {
        return Arrays.stream(value.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
                .filter(token -> token.length() >= 3 && !GENERIC_HOSPITAL_WORDS.contains(token))
                .collect(Collectors.toSet());
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