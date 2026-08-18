package com.nextgen.claims.agent;

import com.nextgen.claims.dto.ClaimDecision;
import com.nextgen.claims.dto.DocumentResult;
import com.nextgen.claims.service.OllamaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ClaimDecisionAgentTest {

    private OllamaService ollamaService;
    private ClaimDecisionAgent agent;

    @BeforeEach
    void setUp() {
        ollamaService = Mockito.mock(OllamaService.class);
        agent = new ClaimDecisionAgent(ollamaService);
        ReflectionTestUtils.setField(agent, "confidenceThreshold", 0.65);
    }

    @Test
    void noValidatedDocumentsGoesToHumanReviewWithoutCallingOllama() {
        DocumentResult irrelevant = DocumentResult.builder()
                .documentId("doc_1").fileName("x.pdf").valid(true).relevant(false).build();

        ClaimDecision decision = agent.decide("clm_1", List.of(irrelevant));

        assertThat(decision.getVerdict()).isEqualTo("UNDER_REVIEW");
        Mockito.verifyNoInteractions(ollamaService);
    }

    @Test
    void ollamaVerdictIsReturnedButConfidenceScoreIsAlwaysThePrecomputedAverage() {
        DocumentResult doc1 = validatedDocument("APPROVE", 0.9, List.of());
        DocumentResult doc2 = validatedDocument("APPROVE", 0.7, List.of());

        when(ollamaService.generateStructured(anyString(), any())).thenReturn(
                ClaimDecision.builder().verdict("AUTO_APPROVED")
                        .confidenceScore(0.4242) // deliberately wrong - the agent must ignore this
                        .reasoning("both documents check out")
                        .recommendedAction("pay out")
                        .keyReasons(List.of("no mismatches"))
                        .build());

        ClaimDecision decision = agent.decide("clm_1", List.of(doc1, doc2));

        assertThat(decision.getVerdict()).isEqualTo("AUTO_APPROVED");
        assertThat(decision.getConfidenceScore()).isEqualTo(0.8); // average of 0.9 and 0.7, not 0.4242
    }

    @Test
    void ollamaFailureFallsBackToUnderReviewInsteadOfCrashing() {
        DocumentResult doc = validatedDocument("APPROVE", 0.9, List.of());
        when(ollamaService.generateStructured(anyString(), any()))
                .thenThrow(new OllamaService.OllamaException("OLLAMA_TIMEOUT", new RuntimeException("timed out")));

        ClaimDecision decision = agent.decide("clm_1", List.of(doc));

        assertThat(decision.getVerdict()).isEqualTo("UNDER_REVIEW");
        assertThat(decision.getReasoning()).contains("OLLAMA_TIMEOUT");
    }

    private DocumentResult validatedDocument(String ollamaDecision, double confidence, List<String> flags) {
        return DocumentResult.builder()
                .documentId("doc_" + System.identityHashCode(ollamaDecision))
                .fileName("doc.pdf").documentType("DISCHARGE_SUMMARY")
                .valid(true).relevant(true)
                .validationResult(new ValidationResult(List.of(), ollamaDecision, flags, "explanation", confidence))
                .build();
    }
}
