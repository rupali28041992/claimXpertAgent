package com.nextgen.claims.docvalidation.service;

import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.repository.PolicyClauseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implementation note (matching the existing
 * com.nextgen.claims.rag.PolicyClauseRetriever, which already established
 * this pattern in this codebase): the current MongoDB is Community
 * edition, which has no native $vectorSearch (Atlas-only). This filters by
 * claimType/claimReason (plain query, using
 * docvalidation.mongodb.policy-vector-index only as a documented/
 * configurable label for when this DOES move to a real Atlas Vector
 * Search index) then ranks the small candidate set by cosine similarity
 * in Java. Swap the body for an Atlas $vectorSearch aggregation later
 * without touching PolicyRagAgent.
 */
@Service
@RequiredArgsConstructor
public class CosineMongoVectorService implements MongoVectorService {

    private final PolicyClauseRepository repository;

    @Override
    public Optional<PolicyClause> findNearestClause(float[] embedding, String claimType, String claimReason) {
        List<PolicyClause> candidates = repository.findByClaimTypeAndClaimReason(claimType, claimReason);
        if (candidates.isEmpty()) {
            candidates = repository.findByClaimType(claimType);
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        return candidates.stream()
                .max(Comparator.comparingDouble(c -> cosineSimilarity(embedding, c.getEmbedding())));
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
