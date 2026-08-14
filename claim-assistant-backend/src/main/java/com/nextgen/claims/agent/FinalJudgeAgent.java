package com.nextgen.claims.agent;

import com.nextgen.claims.dto.AgentFinding;
import com.nextgen.claims.dto.FinalDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinalJudgeAgent {

    private final ChatClient chatClient;

    private static final double W_REQUIREMENT   = 0.25;
    private static final double W_COVERAGE      = 0.40;
    private static final double W_INVESTIGATION = 0.35;

    public FinalDecision synthesize(AgentFinding req, AgentFinding cov, AgentFinding inv) {

        double weightedConfidence =
                req.getConfidenceScore()  * W_REQUIREMENT   +
                cov.getConfidenceScore()  * W_COVERAGE      +
                inv.getConfidenceScore()  * W_INVESTIGATION;

        String prompt = """
                You are the final claims adjudication judge. Three specialist agents have
                independently analyzed this claim. Synthesize their findings into a final decision.

                -- REQUIREMENT AGENT (weight 25%%) --
                Verdict: %s   Confidence: %.2f
                Evidence: %s
                Explanation: %s

                -- POLICY COVERAGE AGENT (weight 40%%) --
                Verdict: %s   Confidence: %.2f
                Evidence: %s
                Explanation: %s

                -- INVESTIGATION AGENT (weight 35%%) --
                Verdict: %s   Confidence: %.2f
                Evidence: %s
                Explanation: %s

                Pre-computed weighted confidence: %.2f

                Decision rules:
                - APPROVED if: coverage is COVERED, investigation is SUPPORTED, requirements are COMPLETE.
                - REJECTED if: coverage is NOT_COVERED, or investigation is SUSPICIOUS with high confidence.
                - HUMAN_REVIEW_REQUIRED for all borderline cases (UNCERTAIN coverage, INCONSISTENT findings,
                  INCOMPLETE requirements with plausible excuse, or weighted confidence below 0.65).

                Return valid JSON with:
                - verdict: "APPROVED", "REJECTED", or "HUMAN_REVIEW_REQUIRED"
                - confidenceScore: use the pre-computed weighted confidence value above exactly
                - reasoning: 2-3 sentence narrative
                - recommendedAction: one actionable sentence for the adjuster or customer
                - keyReasons: array of 3-5 short bullet point strings
                """.formatted(
                        req.getVerdict(), req.getConfidenceScore(), req.getEvidence(), req.getExplanation(),
                        cov.getVerdict(), cov.getConfidenceScore(), cov.getEvidence(), cov.getExplanation(),
                        inv.getVerdict(), inv.getConfidenceScore(), inv.getEvidence(), inv.getExplanation(),
                        weightedConfidence);

        FinalDecision raw = chatClient.prompt().user(prompt).call().entity(FinalDecision.class);
        return FinalDecision.builder()
                .verdict(raw.getVerdict())
                .confidenceScore(weightedConfidence)
                .reasoning(raw.getReasoning())
                .recommendedAction(raw.getRecommendedAction())
                .keyReasons(raw.getKeyReasons())
                .build();
    }
}
