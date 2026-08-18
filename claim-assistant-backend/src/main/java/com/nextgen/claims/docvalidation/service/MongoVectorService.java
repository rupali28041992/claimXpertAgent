package com.nextgen.claims.docvalidation.service;

import com.nextgen.claims.docvalidation.model.PolicyClause;

import java.util.List;

/**
 * All vector-search logic for policy clauses lives behind this one
 * interface (Section 22 of the spec) - no agent talks to MongoDB directly
 * for this purpose.
 */
public interface MongoVectorService {

    /** Never returns null - an empty list means no candidates were found. */
    List<PolicyClause> findTopKClauses(float[] embedding, String claimType, String claimReason, int topK);
}
