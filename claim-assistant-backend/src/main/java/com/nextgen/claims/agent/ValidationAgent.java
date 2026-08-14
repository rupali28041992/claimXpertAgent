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

        String clauseText = clauses.isEmpty()
                ? "No specific policy clause found for this claim type."
                : clauses.stream().map(PolicyClauseVector::getClauseText).collect(Collectors.joining("\n---\n"));

        String answersText = claimAnswers == null ? "" : claimAnswers.stream()
                .map(a -> a.getQuestionId() + ": " + a.getAnswerText())
                .collect(Collectors.joining("\n"));

        // NOTE: dynamic values must NOT be baked into the template string (e.g. via
        // String.formatted/concatenation). ChatClient.user(String) renders the text through
        // Spring AI's PromptTemplate, which wraps an ST4 (StringTemplate 4) template using
        // '{' and '}' as the expression delimiters. If a value such as extractedFields.toString()
        // (which renders as the literal "{}" for an empty map) is embedded in the raw string
        // before it reaches .user(...), ST4 re-parses those braces as template expressions and
        // throws STException. Passing each dynamic value through .param(...) instead makes ST4
        // substitute it as a literal attribute value, never re-lexed as template syntax.
        String template = """
                You are validating one uploaded claim document against the insurer's policy wording.

                POLICY CLAUSE(S):
                {clauseText}

                DOCUMENT TEXT (OCR extracted):
                {ocrText}

                EXTRACTED FIELDS FROM DOCUMENT:
                {extractedFields}

                ANSWERS THE CUSTOMER TYPED IN THE FORM:
                {answersText}

                Check two things only:
                1. Does anything in the document contradict or fail to satisfy the policy clause(s) above
                   (e.g. a waiting-period or exclusion violation)?
                2. Does any extracted field meaningfully mismatch what the customer typed
                   (e.g. a different hospital/garage name, date, or amount - ignore trivial spelling/formatting differences)?

                Respond with flags as short machine-readable codes, e.g. "mismatch:hospitalName" or
                "clause_conflict:waiting_period". Return an empty flags list if nothing is wrong.
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
                        .param("clauseText", clauseText)
                        .param("ocrText", ocrText)
                        .param("extractedFields", extractedFields)
                        .param("answersText", answersText))
                .options(options)
                .call()
                .entity(ValidationResult.class);
    }
}
