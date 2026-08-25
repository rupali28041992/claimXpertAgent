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
    /** Maps original filename → UI-selected document category declared by the user. */
    private Map<String, String> fileDocumentTypes = new java.util.HashMap<>();

    private List<DocumentResult> documents = new ArrayList<>();

    private List<PolicyClause> policyClauses = new ArrayList<>();

    private ClaimDecisionResult decision;

    private ClaimProcessingStatus status;

    public void addDocument(DocumentResult document) {
        this.documents.add(document);
    }
}
