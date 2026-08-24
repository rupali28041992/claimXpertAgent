package com.nextgen.claims.docvalidation.service;

import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.repository.PolicyClauseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * MongoDB policy vector retrieval using cosine similarity.
 *
 * Current implementation is suitable for MongoDB Community / small
 * policy datasets where all clauses can be loaded and ranked in Java.
 *
 * For your current Medical policy:
 *
 *     22 policy clauses
 *
 * This is completely feasible.
 *
 * Later this can be replaced by MongoDB Atlas $vectorSearch without
 * changing PolicyRagAgent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CosineMongoVectorService
        implements MongoVectorService {

    private final PolicyClauseRepository repository;

    @Override
    public List<PolicyClause> findTopKClauses(
            float[] queryEmbedding,
            String claimType,
            int topK) {

        /*
         * IMPORTANT:
         *
         * We filter ONLY by claim type.
         *
         * We DO NOT filter by claimReason.
         *
         * Otherwise a claim such as:
         *
         *     MEDICAL / Hospitalization
         *
         * may never retrieve:
         *
         *     Permanent Exclusions
         *     Waiting Period - Specific Procedures
         *
         * even when the OCR text clearly contains:
         *
         *     dental surgery
         *     appendectomy
         *     cosmetic surgery
         */
        List<PolicyClause> candidates =
                repository.findByClaimType(claimType);

        if (candidates.isEmpty()) {

            log.warn(
                    "[CosineMongoVectorService] No policy clauses found for claimType={}",
                    claimType
            );

            return List.of();
        }

        log.info(
                "[CosineMongoVectorService] Ranking {} policy clauses for claimType={}",
                candidates.size(),
                claimType
        );

        /*
         * Semantic ranking.
         */
        return candidates.stream()
                .filter(c ->
                        c.getEmbedding() != null &&
                                !c.getEmbedding().isEmpty()
                )
                .map(c ->
                        new ScoredClause(
                                c,
                                cosineSimilarity(
                                        queryEmbedding,
                                        c.getEmbedding()
                                )
                        )
                )
                .sorted(
                        Comparator.comparingDouble(
                                ScoredClause::score
                        ).reversed()
                )
                .limit(topK)
                .peek(scored ->
                        log.info(
                                "[CosineMongoVectorService] clause={} similarity={}",
                                scored.clause().getClaimReason(),
                                scored.score()
                        )
                )
                .map(ScoredClause::clause)
                .toList();
    }

    private double cosineSimilarity(
            float[] query,
            List<Double> policyEmbedding) {

        if (query == null ||
                policyEmbedding == null ||
                policyEmbedding.isEmpty()) {

            return 0.0;
        }

        if (query.length != policyEmbedding.size()) {

            log.warn(
                    "[CosineMongoVectorService] Embedding dimension mismatch query={} policy={}",
                    query.length,
                    policyEmbedding.size()
            );
        }

        double dotProduct = 0.0;
        double queryNorm = 0.0;
        double policyNorm = 0.0;

        int dimensions =
                Math.min(
                        query.length,
                        policyEmbedding.size()
                );

        for (int i = 0; i < dimensions; i++) {

            double queryValue = query[i];
            double policyValue = policyEmbedding.get(i);

            dotProduct +=
                    queryValue * policyValue;

            queryNorm +=
                    queryValue * queryValue;

            policyNorm +=
                    policyValue * policyValue;
        }

        if (queryNorm == 0.0 ||
                policyNorm == 0.0) {

            return 0.0;
        }

        return dotProduct /
                (
                        Math.sqrt(queryNorm) *
                                Math.sqrt(policyNorm)
                );
    }

    private record ScoredClause(
            PolicyClause clause,
            double score
    ) {
    }
}