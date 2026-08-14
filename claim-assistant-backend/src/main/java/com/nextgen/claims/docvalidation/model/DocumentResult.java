package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Per-file outcome, nested inside ClaimContext/ClaimResult (Section 27 of the spec). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResult {
    private String fileName;
    private String documentId;
    private boolean valid;
    private List<String> errors;

    private String ocrText;
    private String documentType;

    private boolean relevant;
    private Double relevanceConfidence;
    private Double similarityScore;
    private String relevanceReason;

    private ValidationResult validationResult;

    private DocumentStatus status;
}
