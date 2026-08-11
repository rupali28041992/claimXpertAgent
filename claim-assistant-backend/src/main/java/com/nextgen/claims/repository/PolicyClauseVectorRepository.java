package com.nextgen.claims.repository;

import com.nextgen.claims.model.PolicyClauseVector;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PolicyClauseVectorRepository extends MongoRepository<PolicyClauseVector, String> {

    // Coarse pre-filter by product before the retriever does in-memory
    // cosine similarity ranking over embeddings - keeps the candidate set
    // small without needing a real vector index for the prototype.
    List<PolicyClauseVector> findByProductType(String productType);
}
