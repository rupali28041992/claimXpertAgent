package com.nextgen.claims.agent;

import com.nextgen.claims.model.ClaimAnswer;
import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.rag.PolicyClauseRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    // Overrides the app-wide chat model (qwen3.5:9b, a "thinking" model - slow and, in
    // this Spring AI version, has no API to disable reasoning) with a small non-thinking
    // model for this one classification-style call. IntentClassificationAgent is
    // unaffected and keeps using the default configured in application.yml.
    @Value("${claims.validation.model:qwen2.5:3b}")
    private String validationModel;

    public ValidationResult validate(String claimType,
                                      String claimReason,
                                      String ocrText,
                                      Map<String, String> extractedFields,
                                      List<ClaimAnswer> claimAnswers) {

        List<PolicyClauseVector> clauses = policyClauseRetriever.retrieveRelevantClauses(claimType, claimReason);

        // Top-3 conditions numbered with section header so the LLM knows which clause applies
        String conditionsBlock = clauses.isEmpty()
                ? "No specific policy conditions found for this claim type."
                : IntStream.range(0, clauses.size())
                        .mapToObj(i -> String.format("[%d] %s%n%s",
                                i + 1,
                                clauses.get(i).getSection(),
                                clauses.get(i).getClauseText()))
                        .collect(Collectors.joining("\n\n"));

        // Claim details: type + reason + every answer the customer gave in the questionnaire
        String claimDetails = "Claim Type  : " + claimType + "\n"
                + "Claim Reason: " + claimReason + "\n"
                + (claimAnswers == null || claimAnswers.isEmpty() ? "" :
                        claimAnswers.stream()
                                .map(a -> a.getQuestionId() + ": " + a.getAnswerText())
                                .collect(Collectors.joining("\n")));

        // NOTE: values are passed via .param() so Spring AI's ST4 template engine
        // never re-parses them as template expressions (avoids STException on values
        // like "{}" from an empty map toString).
        String template = """
                You are a claims validation assistant. A customer has submitted an insurance claim.

                TOP 3 POLICY CONDITIONS APPLICABLE TO THIS CLAIM:
                {conditionsBlock}

                CUSTOMER CLAIM DETAILS:
                {claimDetails}

                DOCUMENT TEXT (OCR extracted from uploaded file):
                {ocrText}

                FIELDS EXTRACTED FROM DOCUMENT:
                {extractedFields}

                INSTRUCTIONS:
                Evaluate each of the 3 policy conditions independently against the claim details
                and the uploaded document. For EACH condition decide:
                  - satisfied: true if the claim/document fully meets this condition, false if not
                  - finding: one sentence stating exactly what passes or what specifically fails

                Then, based on all 3 condition results, give an overall decision:
                  APPROVE      — all 3 conditions satisfied and no field mismatches found
                  REJECT       — at least one condition is clearly violated
                  INVESTIGATE  — information is ambiguous or a field mismatch needs human review

                Also return:
                  flags  — machine-readable codes for each problem found, referencing the condition
                           number in brackets, e.g.:
                             "clause_conflict:[1]:min_24h_admission"
                             "clause_conflict:[2]:missing_discharge_summary"
                             "clause_conflict:[3]:no_preauth_cashless"
                             "mismatch:hospitalName"
                           Empty list if nothing is wrong.
                  explanation — one paragraph summarising the overall finding for a claims handler.

                Return ONLY valid JSON matching this exact structure — no extra text:
                {
                  "conditionChecks": [
                    { "condition": "<section label>", "satisfied": true/false, "finding": "<one sentence>" },
                    { "condition": "<section label>", "satisfied": true/false, "finding": "<one sentence>" },
                    { "condition": "<section label>", "satisfied": true/false, "finding": "<one sentence>" }
                  ],
                  "decision": "APPROVE" | "REJECT" | "INVESTIGATE",
                  "flags": ["...", "..."],
                  "explanation": "..."
                }
                """;


        // qwen3.5:9b is a "thinking" model - Spring AI 1.0.0-M6 has no API to disable
        // that (no think/disableThinking() on OllamaOptions in this version), so a
        // hidden reasoning trace can otherwise run unbounded and starve the final JSON
        // answer of context. format("json") forces syntactically valid output at the
        // Ollama server level (BeanOutputConverter alone is prompt-only, no enforcement),
        // and numCtx/numPredict cap the worst case instead of leaving Ollama's defaults.
        OllamaOptions options = OllamaOptions.builder()
                .model(validationModel)
                .format("json")
                .numCtx(8192)
                .numPredict(2048)
                .build();

        return chatClient.prompt()
                .user(u -> u
                        .text(template)
                        .param("conditionsBlock", conditionsBlock)
                        .param("claimDetails", claimDetails)
                        .param("ocrText", ocrText)
                        .param("extractedFields", extractedFields))
                .options(options)
                .call()
                .entity(ValidationResult.class);
    }

    private String formatMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "(none)";
        return map.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
