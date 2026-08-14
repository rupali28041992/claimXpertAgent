package com.nextgen.claims.docvalidation.service;

import com.nextgen.claims.docvalidation.model.PolicyClause;

import java.util.Optional;

/**
 * All vector-search logic for policy clauses lives behind this one
 * interface (Section 22 of the spec) - no agent talks to MongoDB directly
 * for this purpose.
 */
public interface MongoVectorService {

    Optional<PolicyClause> findNearestClause(float[] embedding, String claimType, String claimReason);
}
