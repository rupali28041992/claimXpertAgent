package com.nextgen.claims.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * One uploaded document, produced by OCR + Validation Agent during claim
 * submission. Nested inside Claim.documents. No AI runs before this exists;
 * flags/clauseSatisfied are the ONLY fields the Validation Agent writes.
 *
 * Fields below fileRef/ocrText/extractedFields/flags/clauseSatisfied were
 * added for the POST /api/claims multi-agent pipeline (ClaimOrchestrator) -
 * additive only, the original wizard submit flow (ClaimService) never sets
 * or reads them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimDocument {
    private String docType;
    private String fileRef;
    private String ocrText;
    private Map<String, String> extractedFields;
    private List<String> flags;
    private Boolean clauseSatisfied;

    // -- ClaimOrchestrator pipeline fields --
    private String fileName;
    private String documentId;
    private Boolean valid;
    private List<String> errors;
    private String documentType;
    private Boolean relevant;
    private Double relevanceConfidence;
    private Double similarityScore;
    private String relevanceReason;
    private String decisionSource; // RULE, VECTOR, OLLAMA
    private String status;
    private Double validationConfidence;
    private String validationReason;
}
