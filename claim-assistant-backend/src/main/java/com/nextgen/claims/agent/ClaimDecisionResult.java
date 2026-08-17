package com.nextgen.claims.agent;

import java.util.List;

/**
 * Structured JSON the model is forced to return - the automated routing decision. AutoRoutingStatus
 * deliberately excludes ClaimStatus.APPROVED/REJECTED/PAID/SUBMITTED so the LLM structurally
 * cannot emit an adjuster-only or terminal status; the caller maps this 1:1 to ClaimStatus.
 */
public record ClaimDecisionResult(
        AutoRoutingStatus status,
        String reason,
        List<String> flags
) {
    public enum AutoRoutingStatus {
        AUTO_APPROVED,
        AUTO_REJECTED,
        UNDER_REVIEW
    }
}
