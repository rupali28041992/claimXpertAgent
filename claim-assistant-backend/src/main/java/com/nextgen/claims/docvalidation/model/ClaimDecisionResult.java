package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Output of ClaimDecisionAgent - the one claim-level approve/reject/manual-
 * review call, made once per claim from the combined evidence of every
 * relevant document plus the retrieved policy clauses. Distinct from the
 * per-document ValidationResult, which never decides approval.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimDecisionResult {
    private ClaimDecisionStatus decision;
    private List<String> conditions;
    private List<String> matchedClauses;
    private double confidence;
    private String reason;
}
