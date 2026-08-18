package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Stored in a NEW collection "policy_clauses" (Section 21/31 of the spec) -
 * deliberately not the existing "policy_clause_vectors" collection used by
 * com.nextgen.claims.model.PolicyClauseVector / PolicyClauseRetriever, to
 * avoid any schema collision with the live /api/claims/submit flow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "policy_clauses")
public class PolicyClause {

    @Id
    private String id;

    private String claimType;
    private String claimReason;
    private String clauseText;
    private List<Double> embedding;

    /** Which source PDF this clause was ingested from, e.g. "Medical_Insurance_Policy_ClaimXpert.pdf" - scopes re-ingestion deletes so ingesting one document never wipes clauses ingested from another. */
    private String sourceDocument;
}
