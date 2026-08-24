package com.nextgen.claims.repository;

import com.nextgen.claims.model.Policy;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PolicyRepository extends MongoRepository<Policy, String> {

    // Fallback for documents inserted externally where policyNumber is a regular field
    Optional<Policy> findByPolicyNumber(String policyNumber);
}
