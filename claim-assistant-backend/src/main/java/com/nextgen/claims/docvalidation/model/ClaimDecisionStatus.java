package com.nextgen.claims.docvalidation.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Final claim-level outcome produced by ClaimDecisionAgent. */
public enum ClaimDecisionStatus {
    APPROVED, REJECTED, MANUAL_REVIEW;

    /**
     * Small local models frequently substitute common insurance synonyms
     * (DECLINED, DENIED, Reject) for the exact literal "REJECTED" they were
     * instructed to use. Jackson's default enum deserialization is a strict
     * exact match, so any of those synonyms used to throw and get swallowed
     * into a MANUAL_REVIEW fallback in ClaimDecisionAgent — silently losing
     * a real REJECTED verdict. Normalize known synonyms here instead.
     */
    @JsonCreator
    public static ClaimDecisionStatus fromJson(String value) {
        if (value == null) {
            return MANUAL_REVIEW;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "APPROVED", "APPROVE" -> APPROVED;
            case "REJECTED", "REJECT", "DECLINED", "DECLINE", "DENIED", "DENY" -> REJECTED;
            case "MANUAL_REVIEW", "MANUALREVIEW", "REVIEW" -> MANUAL_REVIEW;
            default -> MANUAL_REVIEW;
        };
    }
}
