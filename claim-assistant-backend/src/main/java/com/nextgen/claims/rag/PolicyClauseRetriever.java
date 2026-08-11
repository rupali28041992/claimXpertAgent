package com.nextgen.claims.rag;

import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.repository.PolicyClauseVectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * The ONLY RAG lookup in this system. Used exclusively by ValidationAgent.
 *
 * Prototype implementation note: real vector search ($vectorSearch) needs
 * MongoDB Atlas. Here we pre-filter by productType (a normal Mongo query)
 * then rank the small remaining candidate set by cosine similarity in
 * memory. Swap the ranking body for an Atlas aggregation later without
 * touching ValidationAgent.
 */
@Component
@RequiredArgsConstructor
public class PolicyClauseRetriever {

    private final PolicyClauseVectorRepository repository;
    private final EmbeddingModel embeddingModel;

    private static final int TOP_K = 3;

    public List<PolicyClauseVector> retrieveRelevantClauses(String claimType, String claimReason) {
        List<PolicyClauseVector> candidates = repository.findByProductType(claimType);
        if (candidates.isEmpty()) {
            return List.of();
        }

        float[] queryEmbedding = embeddingModel.embed("Claim type: " + claimType + ". Reason: " + claimReason);

        return candidates.stream()
                .sorted(Comparator.comparingDouble(
                        (PolicyClauseVector c) -> cosineSimilarity(queryEmbedding, c.getEmbedding())).reversed())
                .limit(TOP_K)
                .toList();
    }

    private double cosineSimilarity(float[] a, List<Double> bList) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length && i < bList.size(); i++) {
            double bVal = bList.get(i);
            dot += a[i] * bVal;
            normA += a[i] * a[i];
            normB += bVal * bVal;
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
