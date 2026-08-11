package com.nextgen.claims.rules;

import java.util.List;

/** One decision-table row's output for a claim type - questions + required documents. */
public record ClaimTypeConfig(
        String claimType,
        List<QuestionDef> questions,
        List<String> requiredDocuments
) {
    public record QuestionDef(String questionId, String questionText, String fieldType) {
    }
}
