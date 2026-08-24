package com.nextgen.claims.docvalidation.service;

import com.nextgen.claims.docvalidation.model.PolicyClause;

import java.util.List;

/**
 * Abstraction over policy vector retrieval.
 *
 * The policy embeddings are permanently stored in MongoDB.
 * During claim submission only the claim query embedding is generated.
 */
public interface MongoVectorService {

    /**
     * Searches policy clauses for the supplied claim type.
     *
     * The actual ranking is performed using cosine similarity
     * between:
     *
     *     claim query embedding
     *
     * and
     *
     *     stored policy clause embeddings.
     *
     * claimReason is intentionally NOT used as a database filter.
     */
    List<PolicyClause> findTopKClauses(
            float[] embedding,
            String claimType,
            int topK
    );
}