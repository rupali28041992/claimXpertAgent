package com.nextgen.claims.docvalidation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OllamaEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Override
    @Cacheable(value = "embeddings", key = "#text")
    public float[] generateEmbedding(String text) {
        return embeddingModel.embed(text);
    }
}
