package com.nextgen.claims.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Seeded once per product/rider at policy onboarding, never written during
 * a claim. Read-only input to the Validation Agent's RAG lookup.
 *
 * NOTE: Mongo Community has no native vector search ($vectorSearch is an
 * Atlas-only feature). For the prototype, embeddings are stored as a plain
 * double[] and matched with in-memory cosine similarity in
 * PolicyClauseRetriever - swap for an Atlas Vector Search index later
 * without changing any caller of that retriever.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "policy_clause_vectors")
public class PolicyClauseVector {

    @Id
    private String id;

    private String productType; // TRAVEL / MEDICAL / MOTOR / LIFE
    private String riderCode;   // null for base policy clauses
    private String section;
    private String clauseText;
    private List<Double> embedding;
}
