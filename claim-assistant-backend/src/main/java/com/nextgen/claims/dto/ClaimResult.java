package com.nextgen.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** POST /api/claims response. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimResult {
    private String claimId;
    private String status; // RECEIVED / PROCESSING / COMPLETED / PARTIALLY_COMPLETED / FAILED
    private List<DocumentResult> documents;
    private String applicablePolicyClauseSection;
    private ClaimDecision finalDecision; // AUTO_APPROVED / AUTO_REJECTED / UNDER_REVIEW - see ClaimDecisionAgent
}
