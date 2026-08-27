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

import java.util.ArrayList;
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

        Optional<String> missingMandatory = checkMandatoryMedicalDocuments(context, validDocuments);

        if (missingMandatory.isPresent()) {
            log.info(
                    "[ClaimDecisionAgent] claim={} manual review — missing mandatory document(s)",
                    context.getClaimId()
            );
            return logAndReturn(context, manualReview(missingMandatory.get()));
        }

        List<PolicyClause> clauses =
                context.getPolicyClauses() == null
                        ? List.of()
                        : context.getPolicyClauses();

        // ---------------------------------------------------------
        // Short circuit
        // ---------------------------------------------------------

        if (validDocuments.isEmpty()) {

            // If documents WERE uploaded but all were rejected as irrelevant to the
            // claim type (e.g. a medical discharge summary under a TRAVEL claim),
            // that is a hard rejection — not a manual review.
            boolean allNotRelevant = !allDocuments.isEmpty()
                    && allDocuments.stream().allMatch(d ->
                            d.getErrors() != null
                            && d.getErrors().contains("DOCUMENT_NOT_RELEVANT"));

            if (allNotRelevant) {
                log.info(
                        "[ClaimDecisionAgent] claim={} rejecting — all documents irrelevant for claimType={}",
                        context.getClaimId(),
                        context.getClaimType()
                );
                return logAndReturn(context, rejected(
                        "The submitted documents are not relevant to claim type '"
                        + context.getClaimType()
                        + "'. Please submit documents appropriate for this claim type."));
            }

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

    // -----------------------------------------------------------------
    // Prompt routing — each claim type gets its own flags + examples
    // -----------------------------------------------------------------

    private String buildPrompt(
            ClaimContext context,
            List<DocumentResult> documents,
            List<PolicyClause> clauses) {

        String claimType = context.getClaimType() == null ? "" : context.getClaimType().toUpperCase();
        if ("TRAVEL".equals(claimType)) {
            return buildTravelPrompt(context, documents, clauses);
        }
        return buildMedicalPrompt(context, documents, clauses);
    }

    // -----------------------------------------------------------------
    // Travel prompt — travel-specific document flags and examples
    // -----------------------------------------------------------------

    private String buildTravelPrompt(
            ClaimContext context,
            List<DocumentResult> documents,
            List<PolicyClause> clauses) {

        String evidenceSummary = buildEvidenceSummary(documents);
        String clausesSummary  = clauses.stream()
                .map(c -> "  [" + c.getClaimReason() + "]: " + c.getClauseText())
                .reduce("", (a, b) -> a + "\n" + b);
        String answersSummary  = buildAnswersSummary(context);

        boolean hasBoardingPass    = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                "BOARDING_PASS".equalsIgnoreCase(d.getEvidence().getDocumentType()));
        boolean hasDelayCert       = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                "DELAY".equalsIgnoreCase(d.getEvidence().getDocumentType()));
        boolean hasBaggageReport   = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                "BAGGAGE".equalsIgnoreCase(d.getEvidence().getDocumentType()));
        boolean hasPolicyBond      = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                "POLICY_BOND".equalsIgnoreCase(d.getEvidence().getDocumentType()));
        boolean hasCancellationDoc = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                "CANCELLATION".equalsIgnoreCase(d.getEvidence().getDocumentType()));
        boolean hasItinerary       = documents.stream().anyMatch(d ->
                d.getEvidence() != null &&
                "ITINERARY".equalsIgnoreCase(d.getEvidence().getDocumentType()));

        return ("/no_think\n"
                + "You are an expert travel insurance claims adjudicator. Produce a single JSON decision.\n\n"
                + "-- VERIFIED DOCUMENT FLAGS (pre-computed, authoritative -- trust these exactly) --\n"
                + "  BOARDING_PASS_PRESENT       : " + hasBoardingPass    + "   <- boarding pass verified\n"
                + "  DELAY_CERT_PRESENT          : " + hasDelayCert       + "   <- Airline Delay Certificate verified\n"
                + "  BAGGAGE_REPORT_PRESENT      : " + hasBaggageReport   + "   <- Baggage Loss / PIR report verified\n"
                + "  POLICY_BOND_PRESENT         : " + hasPolicyBond      + "   <- travel policy bond verified\n"
                + "  CANCELLATION_DOC_PRESENT    : " + hasCancellationDoc + "   <- trip cancellation confirmation verified\n"
                + "  ITINERARY_PRESENT           : " + hasItinerary       + "   <- flight itinerary / booking confirmation verified\n\n"
                + "-- CLAIM DETAILS --\n"
                + "  Claim type   : " + context.getClaimType()   + "\n"
                + "  Claim reason : " + context.getClaimReason() + "\n\n"
                + "-- CLAIMANT QUESTIONNAIRE ANSWERS --\n"
                + answersSummary + "\n"
                + "-- SUBMITTED DOCUMENT EVIDENCE --\n"
                + evidenceSummary + "\n"
                + "-- APPLICABLE POLICY CLAUSES --\n"
                + clausesSummary + "\n\n"
                + "DECISION RULES (apply in order):\n"
                + "1. REJECTED -- only if a clause explicitly EXCLUDES this event (e.g. delay under 4 hrs,\n"
                + "   excluded destination, self-inflicted cancellation). Never reject for missing documents.\n"
                + "2. APPROVED -- if the document flags confirm the required travel documents are present and\n"
                + "   the event matches a coverage clause. Trust the flags -- do not second-guess them.\n"
                + "3. MANUAL_REVIEW -- if evidence is incomplete or ambiguous and no exclusion applies.\n\n"
                + "CRITICAL RULES:\n"
                + "- NEVER mention discharge summaries, hospital bills, or any MEDICAL document in a TRAVEL claim.\n"
                + "- For FLIGHT DELAY: BOARDING_PASS_PRESENT=true AND DELAY_CERT_PRESENT=true = both key documents\n"
                + "  confirmed. Approve if a flight delay clause matches and delay is over 4 hours.\n"
                + "- For BAGGAGE LOSS: BAGGAGE_REPORT_PRESENT=true = PIR/Baggage Loss Report confirmed. Approve\n"
                + "  if a baggage clause matches.\n"
                + "- For TRIP CANCELLATION: A boarding pass was NOT required at the time of cancellation\n"
                + "  (the trip was cancelled before departure, so no boarding pass was issued). Instead, look for:\n"
                + "  CANCELLATION_DOC_PRESENT=true (airline cancellation confirmation) OR\n"
                + "  ITINERARY_PRESENT=true (original booking/itinerary). If POLICY_BOND_PRESENT=true AND\n"
                + "  (CANCELLATION_DOC_PRESENT=true OR ITINERARY_PRESENT=true OR BOARDING_PASS_PRESENT=true)\n"
                + "  AND a trip cancellation clause matches -- decide APPROVED.\n"
                + "- If flags show required documents are present and a clause matches, decide APPROVED.\n"
                + "- Base your decision only on travel clauses and the travel evidence listed above.\n\n"
                + "OUTPUT FORMAT (respond with ONLY valid JSON, no extra text):\n"
                + "{\n"
                + "  \"decision\"      : \"APPROVED\" | \"REJECTED\" | \"MANUAL_REVIEW\",\n"
                + "  \"reason\"        : \"2-4 sentences referencing specific clauses and evidence\",\n"
                + "  \"keyFindings\"   : [\"finding 1\", \"finding 2\", ...],\n"
                + "  \"conditions\"    : [\"any condition or caveat\"],\n"
                + "  \"matchedClauses\": [\"exact clause name from the policy clauses above\"],\n"
                + "  \"confidence\"    : 0.0-1.0\n"
                + "}\n\n"
                + "EXAMPLE -- APPROVED (flight delay, boarding pass + delay cert present):\n"
                + "{\"decision\":\"APPROVED\",\"reason\":\"The boarding pass and Airline Delay Certificate confirm a "
                + "6 hour 25 minute delay on flight AI-131, well above the 4-hour threshold. The Flight Delay clause "
                + "covers this event and no exclusion applies.\",\"keyFindings\":[\"Boarding pass present and verified\","
                + "\"Airline Delay Certificate confirmed -- delay: 6 hrs 25 min (exceeds 4-hour threshold)\","
                + "\"Clause 'Flight Delay -- International' matched\",\"No exclusion applies\"],"
                + "\"conditions\":[],\"matchedClauses\":[\"Flight Delay -- International\"],\"confidence\":0.95}\n\n"
                + "EXAMPLE -- APPROVED (trip cancellation, cancellation confirmation + policy bond present):\n"
                + "{\"decision\":\"APPROVED\",\"reason\":\"The airline cancellation confirmation documents a trip "
                + "cancellation before departure due to a medical emergency. The policy bond confirms active coverage. "
                + "The Trip Cancellation clause covers cancellation for health-related reasons and no exclusion applies.\","
                + "\"keyFindings\":[\"Cancellation confirmation present -- reason: medical emergency\","
                + "\"Policy bond verified -- active coverage confirmed\","
                + "\"Clause 'Trip Cancellation' matched: covers pre-departure cancellation for health reasons\","
                + "\"No exclusion applies\"],"
                + "\"conditions\":[],\"matchedClauses\":[\"Trip Cancellation\"],\"confidence\":0.93}\n\n"
                + "EXAMPLE -- REJECTED (delay under 4 hours, threshold not met):\n"
                + "{\"decision\":\"REJECTED\",\"reason\":\"The Airline Delay Certificate shows a delay of only 2 hours "
                + "10 minutes. The Flight Delay clause requires a minimum delay of 4 consecutive hours. This claim does "
                + "not meet the minimum threshold.\",\"keyFindings\":[\"Boarding pass present\","
                + "\"Airline Delay Certificate confirms delay of 2 hrs 10 min\","
                + "\"Policy requires delay > 4 hours -- threshold not met\","
                + "\"Rejection based on policy threshold, not missing documents\"],"
                + "\"conditions\":[],\"matchedClauses\":[\"Flight Delay -- Domestic\"],\"confidence\":0.97}\n\n"
                + "EXAMPLE -- MANUAL_REVIEW (boarding pass present, no delay certificate submitted):\n"
                + "{\"decision\":\"MANUAL_REVIEW\",\"reason\":\"The boarding pass is confirmed present but no Airline "
                + "Delay Certificate was submitted. The flight delay clause requires an official certificate from the "
                + "carrier confirming the delay duration.\",\"keyFindings\":[\"Boarding pass present and verified\","
                + "\"Airline Delay Certificate: NOT SUBMITTED -- required by clause\","
                + "\"No exclusion applies to flight delay events\","
                + "\"Manual reviewer should request Airline Delay Certificate from carrier\"],"
                + "\"conditions\":[\"Airline Delay Certificate required before approval\"],"
                + "\"matchedClauses\":[\"Flight Delay -- Domestic\"],\"confidence\":0.5}\n\n"
                + "Now produce the JSON decision for the travel claim above:\n");
    }

    // -----------------------------------------------------------------
    // Medical prompt (original logic)
    // -----------------------------------------------------------------

    private String buildMedicalPrompt(
            ClaimContext context,
            List<DocumentResult> documents,
            List<PolicyClause> clauses) {

        String evidenceSummary = buildEvidenceSummary(documents);
        String clausesSummary  = clauses.stream()
                .map(c -> "  [" + c.getClaimReason() + "]: " + c.getClauseText())
                .reduce("", (a, b) -> a + "\n" + b);
        String answersSummary  = buildAnswersSummary(context);

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

        return ("/no_think\n"
                + "You are an expert insurance claims adjudicator. Produce a single JSON decision.\n\n"
                + "-- VERIFIED DOCUMENT FLAGS (pre-computed, authoritative -- trust these exactly) --\n"
                + "  DISCHARGE_SUMMARY_PRESENT : " + hasDischarge + "   <- if true, a valid discharge summary was verified\n"
                + "  BILL_PRESENT              : " + hasBill      + "   <- if true, a valid hospital bill was verified\n\n"
                + "-- CLAIM DETAILS --\n"
                + "  Claim type   : " + context.getClaimType()   + "\n"
                + "  Claim reason : " + context.getClaimReason() + "\n"
                + "  Diagnosis    : " + diagnosis                + "\n\n"
                + "-- CLAIMANT QUESTIONNAIRE ANSWERS --\n"
                + answersSummary + "\n"
                + "-- SUBMITTED DOCUMENT EVIDENCE --\n"
                + evidenceSummary + "\n"
                + "-- APPLICABLE POLICY CLAUSES --\n"
                + clausesSummary + "\n\n"
                + "DECISION RULES (apply in order):\n"
                + "1. REJECTED -- only if a clause explicitly EXCLUDES the diagnosis or event type.\n"
                + "   Missing documents are never a reason to reject.\n"
                + "2. APPROVED -- if at least one coverage clause is satisfied by the evidence and no\n"
                + "   exclusion applies. Use the DISCHARGE_SUMMARY_PRESENT and BILL_PRESENT flags as\n"
                + "   the authoritative source for whether those documents exist.\n"
                + "3. MANUAL_REVIEW -- if evidence is incomplete or ambiguous and no exclusion applies.\n\n"
                + "CRITICAL RULES:\n"
                + "- If DISCHARGE_SUMMARY_PRESENT is true, you MUST NOT say the discharge summary\n"
                + "  is missing or not found. It is verified present.\n"
                + "- If BILL_PRESENT is true, you MUST NOT say the bill is missing.\n"
                + "- A discharge summary alone (without a bill) can satisfy hospitalisation clauses\n"
                + "  if the clause does not explicitly require a bill.\n"
                + "- Base your decision on the clauses and evidence above only.\n\n"
                + "OUTPUT FORMAT (respond with ONLY valid JSON, no extra text):\n"
                + "{\n"
                + "  \"decision\"      : \"APPROVED\" | \"REJECTED\" | \"MANUAL_REVIEW\",\n"
                + "  \"reason\"        : \"2-4 sentences referencing specific clauses and evidence\",\n"
                + "  \"keyFindings\"   : [\"finding 1\", \"finding 2\", ...],\n"
                + "  \"conditions\"    : [\"any condition or caveat, e.g. subject to verification\"],\n"
                + "  \"matchedClauses\": [\"exact clause name from the policy clauses above\"],\n"
                + "  \"confidence\"    : 0.0-1.0\n"
                + "}\n\n"
                + "EXAMPLE -- APPROVED (discharge summary present, no bill required by clause):\n"
                + "{\"decision\":\"APPROVED\",\"reason\":\"The discharge summary confirms Acute Appendicitis. "
                + "The In-patient Hospitalisation clause covers surgical treatment requiring 24+ hour admission "
                + "and does not mandate a separate hospital bill. No exclusion clause applies to Appendicitis.\","
                + "\"keyFindings\":[\"Discharge summary present and verified -- diagnosis: Acute Appendicitis\","
                + "\"Treatment: Laparoscopic Appendectomy\","
                + "\"Clause 'In-patient Hospitalisation' matched: covers surgical in-patient procedures\","
                + "\"No exclusion applies to this diagnosis\"],"
                + "\"conditions\":[],\"matchedClauses\":[\"In-patient Hospitalisation\"],\"confidence\":0.91}\n\n"
                + "EXAMPLE -- REJECTED (diagnosis explicitly excluded by policy):\n"
                + "{\"decision\":\"REJECTED\",\"reason\":\"The claim is for dental treatment. Clause "
                + "'Exclusions -- Dental' explicitly excludes routine and surgical dental procedures. "
                + "This exclusion applies regardless of documents submitted.\","
                + "\"keyFindings\":[\"Diagnosis: dental treatment\","
                + "\"Clause 'Exclusions -- Dental' directly excludes this diagnosis\","
                + "\"Rejection is based on policy exclusion, not missing documents\"],"
                + "\"conditions\":[],\"matchedClauses\":[\"Exclusions -- Dental\"],\"confidence\":0.98}\n\n"
                + "EXAMPLE -- MANUAL_REVIEW (clause requires bill, bill not present):\n"
                + "{\"decision\":\"MANUAL_REVIEW\",\"reason\":\"The In-patient Hospitalisation clause requires "
                + "an itemised hospital bill for claims above Rs.10,000. The discharge summary is present but "
                + "no bill was submitted. The claim cannot be approved without the bill.\","
                + "\"keyFindings\":[\"Discharge summary present and verified -- diagnosis: Typhoid Fever\","
                + "\"Bill: NOT PRESENT -- required by clause for amounts above Rs.10,000\","
                + "\"No exclusion applies to the stated diagnosis\","
                + "\"Manual reviewer should request itemised hospital bill\"],"
                + "\"conditions\":[\"Itemised hospital bill required before approval\"],"
                + "\"matchedClauses\":[\"In-patient Hospitalisation\"],\"confidence\":0.5}\n\n"
                + "Now produce the JSON decision for the claim above:\n");
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

    /**
     * A medical claim needs a Discharge Summary and a Hospital Bill to be
     * approvable — every other document category (Policy Bond, ID Proof,
     * Prescription, etc.) is supplementary and is simply passed through to
     * Ollama as extra evidence, never gated here.
     */
    private Optional<String> checkMandatoryMedicalDocuments(
            ClaimContext context,
            List<DocumentResult> validDocuments) {

        if (!"MEDICAL".equalsIgnoreCase(context.getClaimType())) {
            return Optional.empty();
        }

        boolean hasDischargeSummary = validDocuments.stream().anyMatch(d ->
                "DISCHARGE_SUMMARY".equalsIgnoreCase(d.getEvidence().getDocumentType()));
        boolean hasHospitalBill = validDocuments.stream().anyMatch(d ->
                "HOSPITAL_BILL".equalsIgnoreCase(d.getEvidence().getDocumentType()));

        if (hasDischargeSummary && hasHospitalBill) {
            return Optional.empty();
        }

        List<String> missing = new ArrayList<>();
        if (!hasDischargeSummary) missing.add("Discharge Summary");
        if (!hasHospitalBill) missing.add("Hospital Bill");

        return Optional.of(
                "Missing mandatory document(s) for a medical claim: " + String.join(" and ", missing)
                        + ". Both a Discharge Summary and a Hospital Bill are required before this "
                        + "claim can be approved. Any other submitted documents are treated as "
                        + "supplementary evidence only.");
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
