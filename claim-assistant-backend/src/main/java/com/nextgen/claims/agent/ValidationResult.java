package com.nextgen.claims.agent;

import java.util.List;

/**
 * Structured JSON qwen must return from ValidationAgent.
 *
 * conditionChecks — one entry per retrieved policy condition (up to 3).
 *                   Lets the LLM reason about each condition independently
 *                   before committing to an overall verdict.
 *
 * decision        — overall verdict across all 3 conditions:
 *                   APPROVE   : all conditions satisfied, no mismatches
 *                   REJECT    : one or more conditions clearly violated
 *                   INVESTIGATE : ambiguous — human review needed
 *
 * flags           — machine-readable codes consumed by GoRules / readiness score
 *                   e.g. "clause_conflict:[1]:min_24h_admission", "mismatch:hospitalName"
 *
 * explanation     — one-paragraph summary for the claims handler
 */
public record ValidationResult(
        List<ConditionCheck> conditionChecks,
        String decision,
        List<String> flags,
        String explanation
) {
    /**
     * Per-condition verdict. The LLM fills one of these for each of the
     * top-3 retrieved policy conditions so every condition is explicitly
     * evaluated rather than collapsed into a single boolean.
     */
    public record ConditionCheck(
            String condition,   // section label, e.g. "Section 4.1 – In-patient Hospitalisation"
            boolean satisfied,  // does the submitted claim/document meet this condition?
            String finding      // one sentence: what passes or what specifically fails
    ) {}
}
