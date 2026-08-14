package com.nextgen.claims.agent;

import com.nextgen.claims.dto.AgentFinding;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.rag.PolicyClauseRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PolicyCoverageAgent {

    private final ChatClient chatClient;
    private final PolicyClauseRetriever policyClauseRetriever;

    public AgentFinding analyze(Claim claim) {
        List<PolicyClauseVector> clauses =
                policyClauseRetriever.retrieveRelevantClauses(claim.getClaimType(), claim.getClaimReason());

        String clauseText = clauses.isEmpty()
                ? "No specific policy clauses found for this product type."
                : clauses.stream()
                        .map(c -> "Section " + c.getSection() + ": " + c.getClauseText())
                        .collect(Collectors.joining("\n---\n"));

        String answersText = claim.getAnswers() == null ? "(none)"
                : claim.getAnswers().stream()
                        .map(a -> a.getQuestionId() + ": " + a.getAnswerText())
                        .collect(Collectors.joining("\n"));

        String prompt = """
                You are a policy coverage analyst.

                POLICY CLAUSES (retrieved via semantic search):
                %s

                CLAIM TYPE: %s
                CLAIM REASON: %s
                FREE TEXT DESCRIPTION: %s
                CUSTOMER ANSWERS:
                %s

                Task: Determine if this claim incident is covered by the policy clauses above.
                - COVERED: the incident clearly falls within policy scope and no exclusion applies.
                - NOT_COVERED: an exclusion or waiting-period clause clearly bars this claim.
                - UNCERTAIN: the clauses are ambiguous or insufficient to decide.

                Return valid JSON with:
                - verdict: "COVERED", "NOT_COVERED", or "UNCERTAIN"
                - confidenceScore: number between 0 and 1
                - evidence: array of specific clause sections cited (e.g. ["Section 3.2 - Waiting Period"])
                - explanation: one sentence
                """.formatted(
                        clauseText,
                        claim.getClaimType(),
                        claim.getClaimReason(),
                        claim.getFreeText(),
                        answersText);

        AgentFinding raw = chatClient.prompt().user(prompt).call().entity(AgentFinding.class);
        return AgentFinding.builder()
                .agentName("PolicyCoverageAgent")
                .verdict(raw.getVerdict())
                .confidenceScore(raw.getConfidenceScore())
                .evidence(raw.getEvidence())
                .explanation(raw.getExplanation())
                .build();
    }
}
