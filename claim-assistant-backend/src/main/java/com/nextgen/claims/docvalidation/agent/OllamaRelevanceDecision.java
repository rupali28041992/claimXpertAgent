package com.nextgen.claims.docvalidation.agent;

/** Structured Ollama output for the borderline-relevance fallback (Section 19 of the spec). */
public record OllamaRelevanceDecision(boolean related, String documentType, double confidence, String reason) {
}
