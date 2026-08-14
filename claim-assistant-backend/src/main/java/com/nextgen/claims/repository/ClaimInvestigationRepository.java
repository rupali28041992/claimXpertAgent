package com.nextgen.claims.repository;

import com.nextgen.claims.model.ClaimInvestigation;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ClaimInvestigationRepository extends MongoRepository<ClaimInvestigation, String> {
    Optional<ClaimInvestigation> findByClaimId(String claimId);
}
