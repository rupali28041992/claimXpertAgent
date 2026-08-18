package com.nextgen.claims.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the Spring AI EmbeddingModel bean (nomic-embed-text via
 * Ollama, already configured in application.yml under spring.ai.ollama.embedding).
 * Kept separate from OllamaService/chat reasoning per the "embedding logic
 * separate from LLM reasoning logic" rule.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] generateEmbedding(String text) {
        return embeddingModel.embed(text);
    }

    public double cosineSimilarity(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
