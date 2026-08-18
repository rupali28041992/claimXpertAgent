package com.nextgen.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The ONE claim-level decision for the POST /api/claims pipeline, produced by
 * ClaimDecisionAgent after every document has been through DocumentAgent +
 * (for valid/relevant ones) ValidationAgent. Distinct from ValidationResult,
 * which is per-document.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimDecision {
    private String verdict; // AUTO_APPROVED / AUTO_REJECTED / UNDER_REVIEW
    private double confidenceScore;
    private String reasoning;
    private String recommendedAction;
    private List<String> keyReasons;
}
