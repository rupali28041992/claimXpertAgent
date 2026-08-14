package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Output of DocumentRelevanceAgent (Section 20 of the spec). */
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
    private DecisionSource decisionSource;

    public enum DecisionSource {
        RULE, VECTOR, OLLAMA
    }
}
