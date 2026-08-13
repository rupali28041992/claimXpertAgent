package com.nextgen.claims.repository;

import com.nextgen.claims.model.Claim;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClaimRepository extends MongoRepository<Claim, String> {
}
