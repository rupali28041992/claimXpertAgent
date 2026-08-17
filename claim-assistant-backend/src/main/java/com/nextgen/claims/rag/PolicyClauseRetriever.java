package com.nextgen.claims.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The ONLY RAG lookup in this system. Used by ValidationAgent (per-document check) and
 * ClaimDecisionAgent (claim-level routing decision) with different topK values.
 *
 * Delegates the actual retrieval mechanics to {@link VectorStore} (see
 * {@link MongoCommunityVectorStore}) so a future move to a real vector index (e.g. Atlas)
 * only requires swapping that bean - this class and its callers don't change.
 */
@Component
@RequiredArgsConstructor
public class PolicyClauseRetriever {

    private final VectorStore vectorStore;

    public List<String> retrieveRelevantClauses(String claimType, String claimReason, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query("Claim type: " + claimType + ". Reason: " + claimReason)
                .topK(topK)
                .filterExpression(new FilterExpressionBuilder().eq("productType", claimType).build())
                .build();

        List<Document> results = vectorStore.similaritySearch(request);
        return results.stream().map(Document::getText).toList();
    }
}
