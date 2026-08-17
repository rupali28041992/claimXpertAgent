package com.nextgen.claims.agent;

import com.nextgen.claims.model.ClaimAnswer;
import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.rag.PolicyClauseRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The ONLY mandatory AI + RAG step in the whole flow (Step 2c). Runs once
 * per uploaded file that passed the plain Java checks in Step 2b. Never
 * decides approve/reject itself - it only reports flags that the Java
 * readiness-score formula and GoRules consume afterwards.
 */
@Component
@RequiredArgsConstructor
public class ValidationAgent {

    private final ChatClient chatClient;
    private final PolicyClauseRetriever policyClauseRetriever;

    public ValidationResult validate(String claimType,
                                      String claimReason,
                                      String ocrText,
                                      Map<String, String> extractedFields,
                                      List<ClaimAnswer> claimAnswers) {

        List<PolicyClauseVector> clauses = policyClauseRetriever.retrieveRelevantClauses(claimType, claimReason);

        String clauseText = clauses.isEmpty()
                ? "No specific policy clause found for this claim type."
                : clauses.stream().map(PolicyClauseVector::getClauseText).collect(Collectors.joining("\n---\n"));

        String answersText = claimAnswers == null ? "" : claimAnswers.stream()
                .map(a -> a.getQuestionId() + ": " + a.getAnswerText())
                .collect(Collectors.joining("\n"));

        String extractedFieldsText = extractedFields == null ? "" : extractedFields.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));

        // Escape angle brackets (< and >) that StringTemplate treats as template delimiters.
        // This prevents ST4 compile errors when OCR or policy text contains '<' or '>'.
        String safeClause = escapeST(clauseText);
        String safeOcr = escapeST(ocrText);
        String safeFields = escapeST(extractedFieldsText);
        String safeAnswers = escapeST(answersText);

        String safeClaimType = escapeST(claimType);
        String safeClaimReason = escapeST(claimReason);

        String prompt = """
                You are validating one uploaded claim document against the insurer's policy wording.

                CLAIM TYPE: %s
                CLAIM REASON: %s

                POLICY CLAUSE(S):
                %s

                DOCUMENT TEXT (OCR extracted):
                %s

                EXTRACTED FIELDS FROM DOCUMENT:
                %s

                ANSWERS THE CUSTOMER TYPED IN THE FORM:
                %s

                Check three things:
                1. Is this document even the right kind of document for a %s claim (e.g. a medical
                   bill/discharge summary/prescription for a MEDICAL claim, a repair estimate for a
                   MOTOR claim)? If the document is about something unrelated to this claim type
                   (e.g. a travel itinerary, boarding pass, or hotel invoice submitted for a MEDICAL
                   claim), that is a relevance failure even if the document is a legitimate document
                   of its own kind.
                2. Does anything in the document contradict or fail to satisfy the policy clause(s) above
                   (e.g. a waiting-period or exclusion violation)?
                3. Does any extracted field meaningfully mismatch what the customer typed
                   (e.g. a different hospital/garage name, date, or amount - ignore trivial spelling/formatting differences)?

                Respond with flags as short machine-readable codes, e.g. "irrelevant_document:%s",
                "mismatch:hospitalName", or "clause_conflict:waiting_period". Return an empty flags
                list only if the document is relevant to this claim type AND nothing else is wrong.
                """.formatted(safeClaimType, safeClaimReason, safeClause, safeOcr, safeFields, safeAnswers,
                claimType, claimType);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(ValidationResult.class);
    }

    // Replace ST template delimiters so string content is safe to pass into Spring AI's PromptTemplate (ST4).
    private static String escapeST(String s) {
        if (s == null) return "";
        return s.replace("<", "\\<").replace(">", "\\>");
    }
}
