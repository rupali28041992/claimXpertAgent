package com.nextgen.claims.repository;

import com.nextgen.claims.model.Policy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends MongoRepository<Policy, String> {

    // @Query bypasses Spring Data's @Id mapping so it searches the raw 'policyNumber'
    // field — needed for documents inserted externally where _id is an ObjectId and
    // policyNumber is stored as a separate string field.
    @Query("{ 'policyNumber': ?0 }")
    Optional<Policy> findByPolicyNumber(String policyNumber);

    List<Policy> findByCustomerId(String customerId);

    List<Policy> findByCustomerIdAndActiveTrue(String customerId);
}
