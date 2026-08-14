package com.nextgen.claims.docvalidation.repository;

import com.nextgen.claims.docvalidation.model.ClaimEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for the NEW "docvalidation_claims" collection - distinct from
 * the existing com.nextgen.claims.repository.ClaimRepository, which reads
 * "claims" for the live /api/claims/submit flow.
 */
public interface ClaimEntityRepository extends MongoRepository<ClaimEntity, String> {
}
