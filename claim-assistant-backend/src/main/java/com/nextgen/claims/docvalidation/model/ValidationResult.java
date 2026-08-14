package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured Ollama output for the "does this document satisfy the policy
 * clause, and does it contradict the user's answers" decision (Section 25/26
 * of the spec). Distinct type from com.nextgen.claims.agent.ValidationResult,
 * which belongs to the existing /api/claims/submit flow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationResult {
    private boolean clauseSatisfied;
    private List<String> flags;
    private double confidence;
    private String reason;
}
