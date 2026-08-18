package com.nextgen.claims.dto;

import com.nextgen.claims.model.PolicyClauseVector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared state ClaimOrchestrator threads through DocumentAgent/PolicyRagAgent/ValidationAgent. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimContext {
    private String claimId;
    private String claimType;
    private String claimReason;
    private Map<String, Object> answers;

    @Builder.Default
    private List<DocumentResult> documents = new ArrayList<>();

    /** Best-matching clause, for display; the full ranked list below is what ValidationAgent uses. */
    private PolicyClauseVector policyClause;
    private List<PolicyClauseVector> policyClauses;

    private String status; // RECEIVED / PROCESSING / COMPLETED / PARTIALLY_COMPLETED / FAILED

    public void addDocument(DocumentResult document) {
        if (documents == null) {
            documents = new ArrayList<>();
        }
        documents.add(document);
    }
}
