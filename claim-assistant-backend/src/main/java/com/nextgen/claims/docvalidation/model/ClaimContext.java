package com.nextgen.claims.docvalidation.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared, mutable state passed between ClaimOrchestrator and every agent
 * (Section 9 of the spec) - avoids threading a growing parameter list
 * through each agent call.
 */
@Data
public class ClaimContext {

    private String claimId;
    private String claimType;
    private String claimReason;
    private Map<String, Object> answers;

    private List<DocumentResult> documents = new ArrayList<>();

    private List<PolicyClause> policyClauses = new ArrayList<>();

    private ClaimDecisionResult decision;

    private ClaimProcessingStatus status;

    public void addDocument(DocumentResult document) {
        this.documents.add(document);
    }
}
