package com.nextgen.claims.agent;

import com.nextgen.claims.model.ClaimAnswer;
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

    private static final int TOP_K = 3;

    public ValidationResult validate(String claimType,
                                      String claimReason,
                                      String ocrText,
                                      Map<String, String> extractedFields,
                                      List<ClaimAnswer> claimAnswers) {

        List<String> clauses = policyClauseRetriever.retrieveRelevantClauses(claimType, claimReason, TOP_K);

        String clauseText = clauses.isEmpty()
                ? "No specific policy clause found for this claim type."
                : String.join("\n---\n", clauses);

        String answersText = claimAnswers == null ? "" : claimAnswers.stream()
                .map(a -> a.getQuestionId() + ": " + a.getAnswerText())
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are validating one uploaded claim document against the insurer's policy wording.

                POLICY CLAUSE(S):
                %s

                DOCUMENT TEXT (OCR extracted):
                %s

                EXTRACTED FIELDS FROM DOCUMENT:
                %s

                ANSWERS THE CUSTOMER TYPED IN THE FORM:
                %s

                Check two things only:
                1. Does anything in the document contradict or fail to satisfy the policy clause(s) above
                   (e.g. a waiting-period or exclusion violation)?
                2. Does any extracted field meaningfully mismatch what the customer typed
                   (e.g. a different hospital/garage name, date, or amount - ignore trivial spelling/formatting differences)?

                Respond with flags as short machine-readable codes, e.g. "mismatch:hospitalName" or
                "clause_conflict:waiting_period". Return an empty flags list if nothing is wrong.
                """.formatted(
                PromptTextSanitizer.sanitize(clauseText),
                PromptTextSanitizer.sanitize(ocrText),
                PromptTextSanitizer.sanitize(String.valueOf(extractedFields)),
                PromptTextSanitizer.sanitize(answersText));

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(ValidationResult.class);
    }
}
