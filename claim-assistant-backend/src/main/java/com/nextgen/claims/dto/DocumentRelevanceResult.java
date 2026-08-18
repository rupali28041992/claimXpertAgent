package com.nextgen.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DocumentRelevanceAgent's answer to "is this document about this claim?" - nothing more. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRelevanceResult {
    private boolean related;
    private String documentType;
    private double confidence;
    private String reason;
    private Double similarityScore;
    private String decisionSource; // RULE, VECTOR, OLLAMA
}
