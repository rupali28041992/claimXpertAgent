package com.nextgen.claims.agent;

import com.nextgen.claims.dto.AgentFinding;
import com.nextgen.claims.model.Claim;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class InvestigationAgent {

    private final ChatClient chatClient;

    public AgentFinding analyze(Claim claim) {
        String answersText = claim.getAnswers() == null ? "(none)"
                : claim.getAnswers().stream()
                        .map(a -> a.getQuestionId() + ": " + a.getAnswerText())
                        .collect(Collectors.joining("\n"));

        String docSummary = claim.getDocuments() == null ? "(no documents)"
                : claim.getDocuments().stream()
                        .map(d -> "--- Document: " + d.getDocType() + " ---\n"
                                + "OCR Text: " + truncate(d.getOcrText(), 400) + "\n"
                                + "Extracted Fields: " + formatMap(d.getExtractedFields()) + "\n"
                                + "Validation Flags: " + d.getFlags())
                        .collect(Collectors.joining("\n"));

        String prompt = """
                You are a claims investigation analyst. Your job is to find inconsistencies.

                CUSTOMER'S FORM ANSWERS:
                %s

                SUBMITTED DOCUMENTS (OCR text and extracted fields):
                %s

                Task: Compare what the customer stated with what the documents actually show.
                Look for: dates that don't match, different hospital/garage/location names,
                amount discrepancies, suspicious gaps. Ignore minor spelling or formatting differences.

                - SUPPORTED: answers and documents are consistent.
                - INCONSISTENT: minor or explainable discrepancies found.
                - SUSPICIOUS: significant contradictions or patterns suggesting misrepresentation.

                Return valid JSON with:
                - verdict: "SUPPORTED", "INCONSISTENT", or "SUSPICIOUS"
                - confidenceScore: number between 0 and 1
                - evidence: array of specific discrepancy descriptions (empty array if SUPPORTED)
                - explanation: one sentence
                """.formatted(answersText, docSummary);

        AgentFinding raw = chatClient.prompt().user(prompt).call().entity(AgentFinding.class);
        return AgentFinding.builder()
                .agentName("InvestigationAgent")
                .verdict(raw.getVerdict())
                .confidenceScore(raw.getConfidenceScore())
                .evidence(raw.getEvidence())
                .explanation(raw.getExplanation())
                .build();
    }

    private String truncate(String text, int max) {
        if (text == null) return "(none)";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String formatMap(java.util.Map<String, String> map) {
        if (map == null || map.isEmpty()) return "(none)";
        return map.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
