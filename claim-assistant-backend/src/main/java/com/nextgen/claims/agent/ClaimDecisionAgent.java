package com.nextgen.claims.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.ClaimDocument;
import com.nextgen.claims.rag.PolicyClauseRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The automated approve/reject/under-review decision - fully replaces GoRules routing
 * (RulesEngineService.route()/claim-routing.json). Decides directly from retrieved policy
 * clauses, the customer's answers, and every uploaded document's extracted content, rather
 * than a plain-Java threshold/flag-count check.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimDecisionAgent {

    private final ChatClient chatClient;
    private final PolicyClauseRetriever policyClauseRetriever;
    private final ObjectMapper objectMapper;

    @Value("${claims.rag.top-k:5}")
    private int topK;

    public ClaimDecisionResult decide(Claim claim) {
        List<String> clauses = policyClauseRetriever.retrieveRelevantClauses(
                claim.getClaimType(), claim.getClaimReason(), topK);

        String clauseText = clauses.isEmpty()
                ? "No specific policy clause found for this claim type."
                : String.join("\n---\n", clauses);

        String documentsText = claim.getDocuments() == null || claim.getDocuments().isEmpty()
                ? "No documents submitted."
                : claim.getDocuments().stream().map(this::describeDocument).collect(Collectors.joining("\n---\n"));

        String answersText = claim.getAnswers() == null ? "" : claim.getAnswers().stream()
                .map(a -> a.getQuestionId() + ": " + a.getAnswerText())
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are an insurance claims adjuster assistant. Decide whether to automatically
                approve, automatically reject, or route this claim to a human adjuster for review.

                POLICY CLAUSE(S) RELEVANT TO THIS CLAIM TYPE:
                %s

                UPLOADED DOCUMENTS (extracted text, per-document flags from an earlier check):
                %s

                ANSWERS THE CUSTOMER TYPED IN THE FORM:
                %s

                INFORMATIONAL CONTEXT (do not treat as decisive on their own):
                readinessScore=%d, flagsAtSubmission=%s

                Decide status as exactly one of these three literal values - no other value is
                valid: "AUTO_APPROVED", "AUTO_REJECTED", "UNDER_REVIEW".
                - AUTO_APPROVED: the documents and answers clearly satisfy the policy clause(s)
                  above, with no contradictions or missing information.
                - AUTO_REJECTED: the documents or answers clearly violate a policy clause above
                  (e.g. an exclusion or waiting-period violation) beyond reasonable doubt.
                - UNDER_REVIEW: anything is ambiguous, contradictory, missing extractable text,
                  or you are not confident enough to auto-approve or auto-reject.

                Give a short, specific reason a human adjuster could read to understand the
                decision, and a list of short machine-readable flag codes (empty if none).

                IMPORTANT: Respond ONLY with a valid JSON object in this exact format (no markdown):
                {"status": "AUTO_APPROVED", "reason": "your reason", "flags": []}
                Valid status values: AUTO_APPROVED, AUTO_REJECTED, UNDER_REVIEW
                """.formatted(
                PromptTextSanitizer.sanitize(clauseText),
                PromptTextSanitizer.sanitize(documentsText),
                PromptTextSanitizer.sanitize(answersText),
                claim.getReadinessScore() == null ? 0 : claim.getReadinessScore(),
                PromptTextSanitizer.sanitize(String.valueOf(claim.getFlagsAtSubmission())));

        // Use .content() instead of .entity() — same reason as ValidationAgent: entity() appends
        // JSON format instructions to systemText which then goes through PromptTemplate (ST4).
        try {
            String raw = chatClient.prompt()
                    .messages(new UserMessage(prompt))
                    .call()
                    .content();
            return objectMapper.readValue(extractJson(raw), ClaimDecisionResult.class);
        } catch (Exception e) {
            log.warn("Claim decision agent call failed for claim {} ({}: {}); routing to UNDER_REVIEW",
                    claim.getClaimId(), e.getClass().getSimpleName(), e.getMessage());
            return new ClaimDecisionResult(
                    ClaimDecisionResult.AutoRoutingStatus.UNDER_REVIEW,
                    "Automated decision unavailable - routed for manual review",
                    List.of());
        }
    }

    private static String extractJson(String response) {
        if (response == null) return "{}";
        int start = response.indexOf("```json");
        if (start >= 0) {
            start += 7;
            int end = response.indexOf("```", start);
            if (end > start) return response.substring(start, end).strip();
        }
        start = response.indexOf("```");
        if (start >= 0) {
            start += 3;
            int end = response.indexOf("```", start);
            if (end > start) return response.substring(start, end).strip();
        }
        int open = response.indexOf('{');
        int close = response.lastIndexOf('}');
        return open >= 0 && close > open ? response.substring(open, close + 1) : response;
    }

    private String describeDocument(ClaimDocument document) {
        String text = document.getOcrText() == null || document.getOcrText().isBlank()
                ? "(no extractable text)"
                : document.getOcrText();
        return "%s: %s | extractedFields=%s | flags=%s | clauseSatisfied=%s".formatted(
                document.getDocType(), text, document.getExtractedFields(),
                document.getFlags(), document.getClauseSatisfied());
    }
}
