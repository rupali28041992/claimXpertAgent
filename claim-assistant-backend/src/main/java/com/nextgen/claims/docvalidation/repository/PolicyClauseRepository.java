package com.nextgen.claims.docvalidation.repository;

import com.nextgen.claims.docvalidation.model.PolicyClause;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Repository for the NEW "policy_clauses" collection (Section 21/22 of the
 * spec) - distinct from the existing
 * com.nextgen.claims.repository.PolicyClauseVectorRepository, which reads
 * "policy_clause_vectors" for the live /api/claims/submit flow.
 */
public interface PolicyClauseRepository extends MongoRepository<PolicyClause, String> {

    List<PolicyClause> findByClaimTypeAndClaimReason(String claimType, String claimReason);

    List<PolicyClause> findByClaimType(String claimType);
}
