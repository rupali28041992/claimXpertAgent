package com.nextgen.claims.rules;

import com.nextgen.claims.model.ClaimDocument;
import org.springframework.stereotype.Component;

import java.util.List;

/** Screen 5's score. Plain weighted formula - no AI, reads only what's already on the claim. */
@Component
public class ReadinessScoreCalculator {

    public int calculate(List<ClaimDocument> documents, int requiredDocumentCount) {
        if (requiredDocumentCount == 0) {
            return 100;
        }

        double docsCompleteRatio = documents.size() / (double) requiredDocumentCount;
        long flagCount = documents.stream()
                .mapToLong(d -> d.getFlags() == null ? 0 : d.getFlags().size())
                .sum();

        double score = 100 * docsCompleteRatio;
        score -= flagCount * 15; // each flag knocks off 15 points
        return (int) Math.max(0, Math.min(100, score));
    }

    public List<String> collectFlags(List<ClaimDocument> documents) {
        return documents.stream()
                .filter(d -> d.getFlags() != null)
                .flatMap(d -> d.getFlags().stream())
                .toList();
    }
}
