package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.service.EmbeddingService;
import com.nextgen.claims.docvalidation.service.MongoVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Answers: "what policy clauses apply to this claim?" (Section 21 of the
 * spec). Called ONCE per claim by ClaimOrchestrator (Section 23) - never
 * once per document. Returns the top-K most relevant clauses (not just one)
 * so a claim-level decision can weigh multiple applicable sections together
 * (e.g. hospitalisation rules AND a waiting-period exclusion at once).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyRagAgent {

    private final EmbeddingService embeddingService;
    private final MongoVectorService mongoVectorService;

    /** Never returns null - an empty list means nothing relevant was found. */
    public List<PolicyClause> findRelevantClauses(String claimType, String claimReason, int topK) {
        String query = claimType + " " + claimReason;
        float[] embedding = embeddingService.generateEmbedding(query);

        List<PolicyClause> clauses = mongoVectorService.findTopKClauses(embedding, claimType, claimReason, topK);

        if (clauses.isEmpty()) {
            log.info("[PolicyRagAgent] no clauses found for claimType={} claimReason={}", claimType, claimReason);
            return List.of();
        }

        String summary = clauses.stream()
                .map(c -> c.getClaimReason() + " (id=" + c.getId() + ")")
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
        log.info("[PolicyRagAgent] retrieved {} clauses for claimType={} claimReason={}: {}",
                clauses.size(), claimType, claimReason, summary);
        return clauses;
    }
}
