package com.nextgen.claims.agent;

import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.rag.PolicyClauseRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Answers "what policy clause applies to this claim?" - called ONCE per
 * claim by ClaimOrchestrator (not once per document) and the result is
 * reused for every document's ValidationAgent call.
 *
 * Delegates the actual Mongo query + cosine-similarity ranking to the
 * existing PolicyClauseRetriever (rag package) rather than duplicating that
 * logic - this class only owns the "once per claim" orchestration contract.
 */
@Component
@RequiredArgsConstructor
public class PolicyRagAgent {

    private final PolicyClauseRetriever policyClauseRetriever;

    public List<PolicyClauseVector> findClauses(String claimType, String claimReason) {
        return policyClauseRetriever.retrieveRelevantClauses(claimType, claimReason);
    }

    /** Best-matching single clause, for display purposes (e.g. ClaimResult.applicablePolicyClauseSection). */
    public PolicyClauseVector findTopClause(String claimType, String claimReason) {
        List<PolicyClauseVector> clauses = findClauses(claimType, claimReason);
        return clauses.isEmpty() ? null : clauses.get(0);
    }
}
