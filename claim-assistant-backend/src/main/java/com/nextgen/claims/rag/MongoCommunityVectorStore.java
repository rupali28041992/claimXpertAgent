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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Spring AI {@link VectorStore} backed by {@code policy_clause_vectors}, ranked by
 * in-memory cosine similarity - MongoDB Community has no native $vectorSearch (Atlas-only).
 * Implementing the standard VectorStore interface (rather than a bespoke retriever) means a
 * future move to Atlas is a bean swap for {@code MongoDBAtlasVectorStore}, with no change to
 * any caller.
 *
 * <p>Filter expressions are only ever a single {@code productType == '...'} equality in this
 * codebase (see {@link PolicyClauseRetriever}), so this deliberately doesn't implement a
 * general Filter.Expression-to-Mongo-Criteria converter - it just pulls that one field/value
 * pair out and reuses the existing {@link PolicyClauseVectorRepository#findByProductType}
 * derived query as the pre-filter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoCommunityVectorStore implements VectorStore {

    private final PolicyClauseVectorRepository repository;
    private final EmbeddingModel embeddingModel;

    @Value("${claims.rag.max-candidates:2000}")
    private int maxCandidates;

    @Override
    public void add(List<Document> documents) {
        List<PolicyClauseVector> rows = documents.stream()
                .map(this::toPolicyClauseVector)
                .toList();
        repository.saveAll(rows);
    }

    @Override
    public void delete(List<String> idList) {
        repository.deleteAllById(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        String productType = extractProductType(filterExpression)
                .orElseThrow(() -> new UnsupportedOperationException(
                        "Only a single productType == '...' filter expression is supported"));
        repository.deleteAll(repository.findByProductType(productType));
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String productType = request.hasFilterExpression()
                ? extractProductType(request.getFilterExpression()).orElse(null)
                : null;

        List<PolicyClauseVector> candidates = productType != null
                ? repository.findByProductType(productType)
                : repository.findAll();

        if (candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() > maxCandidates) {
            log.warn("Candidate set ({}) exceeds claims.rag.max-candidates ({}); truncating - "
                    + "in-memory cosine similarity over MongoDB Community won't scale past this.",
                    candidates.size(), maxCandidates);
            candidates = candidates.subList(0, maxCandidates);
        }

        float[] queryEmbedding = embeddingModel.embed(request.getQuery());

        return candidates.stream()
                .map(row -> toScoredDocument(row, queryEmbedding))
                .filter(doc -> doc.getScore() >= request.getSimilarityThreshold())
                .sorted(Comparator.comparingDouble(Document::getScore).reversed())
                .limit(request.getTopK())
                .toList();
    }

    private PolicyClauseVector toPolicyClauseVector(Document document) {
        var metadata = document.getMetadata();
        return PolicyClauseVector.builder()
                .productType((String) metadata.get("productType"))
                .riderCode((String) metadata.get("riderCode"))
                .section((String) metadata.get("section"))
                .sourceFileName((String) metadata.get("sourceFileName"))
                .clauseText(document.getText())
                .embedding(toDoubleList(embeddingModel.embed(document.getText())))
                .build();
    }

    private Document toScoredDocument(PolicyClauseVector row, float[] queryEmbedding) {
        double score = cosineSimilarity(queryEmbedding, row.getEmbedding());
        return Document.builder()
                .id(row.getId())
                .text(row.getClauseText())
                .metadata("productType", row.getProductType())
                .score(score)
                .build();
    }

    private Optional<String> extractProductType(Filter.Expression expression) {
        if (expression.type() == Filter.ExpressionType.EQ
                && expression.left() instanceof Filter.Key key
                && key.key().equals("productType")
                && expression.right() instanceof Filter.Value value) {
            return Optional.of((String) value.value());
        }
        return Optional.empty();
    }

    private List<Double> toDoubleList(float[] embedding) {
        List<Double> result = new java.util.ArrayList<>(embedding.length);
        for (float f : embedding) {
            result.add((double) f);
        }
        return result;
    }

    private double cosineSimilarity(float[] a, List<Double> bList) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length && i < bList.size(); i++) {
            double bVal = bList.get(i);
            dot += a[i] * bVal;
            normA += a[i] * a[i];
            normB += bVal * bVal;
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
