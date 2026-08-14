package com.nextgen.claims.docvalidation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Wraps the EmbeddingModel bean already provided by the existing
 * spring-ai-ollama-spring-boot-starter dependency (see application.yml's
 * spring.ai.ollama.embedding.model) - no new AI client/config is created,
 * this reuses exactly what com.nextgen.claims.rag.PolicyClauseRetriever
 * already depends on.
 */
@Service
@RequiredArgsConstructor
public class OllamaEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Override
    public float[] generateEmbedding(String text) {
        return embeddingModel.embed(text);
    }
}
