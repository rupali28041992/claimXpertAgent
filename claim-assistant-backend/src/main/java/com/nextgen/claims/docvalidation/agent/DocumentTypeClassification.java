package com.nextgen.claims.docvalidation.agent;

/** Structured Ollama output for document type classification (Section 15 of the spec). */
public record DocumentTypeClassification(String documentType, double confidence) {
}
