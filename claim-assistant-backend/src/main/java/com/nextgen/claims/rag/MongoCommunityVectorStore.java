package com.nextgen.claims.rag;

import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.repository.PolicyClauseVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Local MongoDB-backed VectorStore. Implements Spring AI's VectorStore interface
 * so PolicyClauseRetriever and ClaimDecisionAgent work without change.
 *
 * Uses in-memory cosine similarity (pre-filtered by productType) instead of a
 * real vector index. Swap this bean for MongoDBAtlasVectorStore when moving to Atlas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoCommunityVectorStore implements VectorStore {

    private final PolicyClauseVectorRepository repository;
    private final EmbeddingModel embeddingModel;

    // ── VectorStore.add ───────────────────────────────────────────────────────

    @Override
    public void add(List<Document> documents) {
        for (Document doc : documents) {
            float[] raw = embeddingModel.embed(doc.getText());
            String productType = (String) doc.getMetadata().getOrDefault("productType", "UNKNOWN");
            String section     = (String) doc.getMetadata().getOrDefault("section", "");

            repository.save(PolicyClauseVector.builder()
                    .id(doc.getId())
                    .productType(productType)
                    .section(section)
                    .clauseText(doc.getText())
                    .embedding(toDoubleList(raw))
                    .build());
        }
        log.debug("VectorStore.add — {} documents stored", documents.size());
    }

    // ── VectorStore.delete ────────────────────────────────────────────────────

    @Override
    public void delete(List<String> idList) {
        idList.forEach(repository::deleteById);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        // Not needed for our read-path use case — deletions go through the repository directly
    }

    // ── VectorStore.similaritySearch ──────────────────────────────────────────

    @Override
    public List<Document> similaritySearch(String query) {
        return similaritySearch(SearchRequest.builder().query(query).topK(4).build());
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String query      = request.getQuery();
        int    topK       = request.getTopK() > 0 ? request.getTopK() : 4;
        double threshold  = request.getSimilarityThreshold(); // default 0.0 = accept all

        // Pre-filter by productType parsed from query ("Claim type: MEDICAL. Reason: ...")
        String productType = parseProductType(query);
        List<PolicyClauseVector> candidates = productType != null
                ? repository.findByProductType(productType)
                : (List<PolicyClauseVector>) repository.findAll();

        if (candidates.isEmpty()) return List.of();

        float[] queryVec = embeddingModel.embed(query);

        return candidates.stream()
                .map(c -> Map.entry(c, cosineSimilarity(queryVec, c.getEmbedding())))
                .filter(e -> e.getValue() >= threshold)
                .sorted(Map.Entry.<PolicyClauseVector, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    PolicyClauseVector c = e.getKey();
                    return new Document(
                            c.getId(),
                            c.getClauseText(),
                            Map.of("productType", c.getProductType(), "section", c.getSection()));
                })
                .collect(Collectors.toList());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Extracts "MEDICAL" from "Claim type: MEDICAL. Reason: ..." */
    private String parseProductType(String query) {
        if (query == null) return null;
        final String PREFIX = "Claim type: ";
        int start = query.indexOf(PREFIX);
        if (start < 0) return null;
        start += PREFIX.length();
        int dot = query.indexOf('.', start);
        return dot > start ? query.substring(start, dot).trim() : null;
    }

    private double cosineSimilarity(float[] a, List<Double> b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length && i < b.size(); i++) {
            double bv = b.get(i);
            dot   += a[i] * bv;
            normA += a[i] * a[i];
            normB += bv   * bv;
        }
        return (normA == 0 || normB == 0) ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<Double> toDoubleList(float[] raw) {
        List<Double> list = new ArrayList<>(raw.length);
        for (float v : raw) list.add((double) v);
        return list;
    }
}
