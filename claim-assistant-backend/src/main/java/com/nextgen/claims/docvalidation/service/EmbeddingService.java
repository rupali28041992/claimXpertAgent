package com.nextgen.claims.docvalidation.service;

/**
 * Embedding contract for this pipeline (Section 17 of the spec). Kept
 * separate from reasoning/LLM calls (OllamaService) per the spec's rule
 * not to mix embedding and reasoning in the same class.
 */
public interface EmbeddingService {

    float[] generateEmbedding(String text);
}
