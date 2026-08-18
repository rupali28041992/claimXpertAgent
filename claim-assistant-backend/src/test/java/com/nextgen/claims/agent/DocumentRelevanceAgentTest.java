package com.nextgen.claims.agent;

import com.nextgen.claims.service.EmbeddingService;
import com.nextgen.claims.service.OllamaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class DocumentRelevanceAgentTest {

    private EmbeddingService embeddingService;
    private OllamaService ollamaService;
    private DocumentRelevanceAgent agent;

    private static final float[] CLAIM_VECTOR = {1f, 0f, 0f};

    @BeforeEach
    void setUp() {
        embeddingService = Mockito.mock(EmbeddingService.class);
        ollamaService = Mockito.mock(OllamaService.class);
        agent = new DocumentRelevanceAgent(embeddingService, ollamaService);
        ReflectionTestUtils.setField(agent, "highThreshold", 0.80);
        ReflectionTestUtils.setField(agent, "lowThreshold", 0.60);

        when(embeddingService.generateEmbedding(anyString())).thenReturn(CLAIM_VECTOR);
    }

    @Test
    void clearlyRelatedDocumentIsDecidedByVectorAlone() {
        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.95);

        var result = agent.evaluate("HEALTH", "HOSPITALIZATION", Map.of("hospitalName", "Apollo Hospital"),
                "Apollo Hospital discharge summary for John Doe");

        assertThat(result.isRelated()).isTrue();
        assertThat(result.getDecisionSource()).isEqualTo("VECTOR");
        assertThat(result.getDocumentType()).isEqualTo("DISCHARGE_SUMMARY");
        Mockito.verifyNoInteractions(ollamaService);
    }

    @Test
    void clearlyUnrelatedDocumentIsDecidedByVectorAlone() {
        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.20);

        var result = agent.evaluate("HEALTH", "HOSPITALIZATION", Map.of(),
                "Vehicle registration certificate, chassis number XYZ123");

        assertThat(result.isRelated()).isFalse();
        assertThat(result.getDecisionSource()).isEqualTo("VECTOR");
        assertThat(result.getDocumentType()).isEqualTo("VEHICLE_DOCUMENT");
        Mockito.verifyNoInteractions(ollamaService);
    }

    @Test
    void borderlineSimilarityFallsThroughToOllama() {
        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.70);
        when(ollamaService.generateStructured(anyString(), eq(DocumentRelevanceAgent.OllamaRelevanceResponse.class)))
                .thenReturn(new DocumentRelevanceAgent.OllamaRelevanceResponse(
                        true, "DISCHARGE_SUMMARY", 0.9, "Matches hospitalization claim"));

        var result = agent.evaluate("HEALTH", "HOSPITALIZATION", Map.of(), "ambiguous hospital-ish text");

        assertThat(result.isRelated()).isTrue();
        assertThat(result.getDecisionSource()).isEqualTo("OLLAMA");
        assertThat(result.getConfidence()).isEqualTo(0.9);
    }

    @Test
    void ollamaFailureOnBorderlineFallsBackToSimilarityMidpointInsteadOfCrashing() {
        when(embeddingService.cosineSimilarity(any(), any())).thenReturn(0.70); // above midpoint 0.70
        when(ollamaService.generateStructured(anyString(), any()))
                .thenThrow(new OllamaService.OllamaException("OLLAMA_UNAVAILABLE", new RuntimeException("connect failed")));

        var result = agent.evaluate("HEALTH", "HOSPITALIZATION", Map.of(), "ambiguous text");

        assertThat(result.getDecisionSource()).isEqualTo("VECTOR");
        assertThat(result.getReason()).contains("OLLAMA_UNAVAILABLE");
    }
}
