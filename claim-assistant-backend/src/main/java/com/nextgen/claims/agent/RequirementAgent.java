package com.nextgen.claims.agent;

import com.nextgen.claims.dto.AgentFinding;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.ClaimDocument;
import com.nextgen.claims.rules.ClaimTypeConfig;
import com.nextgen.claims.rules.RulesEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RequirementAgent {

    private final ChatClient chatClient;
    private final RulesEngineService rulesEngineService;

    public AgentFinding analyze(Claim claim) {
        ClaimTypeConfig config = rulesEngineService.getClaimTypeConfig(claim.getClaimType());
        List<String> required = config.requiredDocuments();

        List<String> submitted = claim.getDocuments() == null ? List.of()
                : claim.getDocuments().stream().map(ClaimDocument::getDocType).toList();

        String answersText = claim.getAnswers() == null ? "(none)"
                : claim.getAnswers().stream()
                        .map(a -> a.getQuestionId() + ": " + a.getAnswerText())
                        .collect(Collectors.joining("\n"));

        String prompt = """
                You are a claims document completeness checker.

                CLAIM TYPE: %s
                REQUIRED DOCUMENTS (per policy): %s
                SUBMITTED DOCUMENT TYPES: %s
                CUSTOMER FORM ANSWERS:
                %s

                Task: Decide if document requirements are met. Be lenient about naming variants
                (e.g. "Police Report" matches "FIR"). List any document types that appear
                genuinely missing as evidence items.

                Return valid JSON with these exact fields:
                - verdict: "COMPLETE" or "INCOMPLETE"
                - confidenceScore: number between 0 and 1
                - evidence: array of strings (missing item names, or empty array if COMPLETE)
                - explanation: one sentence summary
                """.formatted(claim.getClaimType(), required, submitted, answersText);

        AgentFinding raw = chatClient.prompt().user(prompt).call().entity(AgentFinding.class);
        return AgentFinding.builder()
                .agentName("RequirementAgent")
                .verdict(raw.getVerdict())
                .confidenceScore(raw.getConfidenceScore())
                .evidence(raw.getEvidence())
                .explanation(raw.getExplanation())
                .build();
    }
}
