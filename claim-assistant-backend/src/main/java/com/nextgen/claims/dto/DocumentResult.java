package com.nextgen.claims.dto;

import com.nextgen.claims.agent.ValidationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Per-document outcome as it moves through DocumentAgent's pipeline. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResult {
    private String documentId;
    private String fileName;
    private String fileRef;
    private boolean valid;
    private List<String> errors;
    private String ocrText;
    private String documentType;
    private boolean relevant;
    private Double relevanceConfidence;
    private Double similarityScore;
    private String relevanceReason;
    private String decisionSource; // RULE, VECTOR, OLLAMA
    private ValidationResult validationResult;

    // RECEIVED / VALIDATING_FILE / OCR_PROCESSING / OCR_COMPLETED / CHECKING_RELEVANCE /
    // RELEVANT / IRRELEVANT / VALIDATING_POLICY / COMPLETED / FAILED
    private String status;
}
