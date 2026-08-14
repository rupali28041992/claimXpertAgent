package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.service.EmbeddingService;
import com.nextgen.claims.docvalidation.service.MongoVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Answers: "what policy clause applies to this claim?" (Section 21 of the
 * spec). Called ONCE per claim by ClaimOrchestrator (Section 23) - never
 * once per document.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyRagAgent {

    private final EmbeddingService embeddingService;
    private final MongoVectorService mongoVectorService;

    public PolicyClause findClause(String claimType, String claimReason) {
        String query = claimType + " " + claimReason;
        float[] embedding = embeddingService.generateEmbedding(query);

        Optional<PolicyClause> clause = mongoVectorService.findNearestClause(embedding, claimType, claimReason);

        if (clause.isEmpty()) {
            log.info("[PolicyRagAgent] no clause found for claimType={} claimReason={}", claimType, claimReason);
            return null;
        }

        log.info("[PolicyRagAgent] clause={}", clause.get().getId());
        return clause.get();
    }
}
