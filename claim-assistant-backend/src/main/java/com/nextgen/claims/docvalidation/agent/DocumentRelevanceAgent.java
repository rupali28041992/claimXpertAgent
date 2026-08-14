package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.config.DocValidationProperties;
import com.nextgen.claims.docvalidation.model.DocumentRelevanceResult;
import com.nextgen.claims.docvalidation.service.EmbeddingService;
import com.nextgen.claims.docvalidation.service.OllamaService;
import com.nextgen.claims.docvalidation.service.OllamaServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Answers ONLY: "is this uploaded document relevant to this claim?"
 * (Section 13/14 of the spec). Does NOT decide policy satisfaction or
 * claim approval - that is PolicyRagAgent/ValidationAgent's job.
 *
 * Hybrid pipeline (Section 14): classify document type, embed + compare
 * against a semantic description of the claim, then only fall back to
 * Ollama's judgment for borderline similarity scores - clearly
 * high/low-confidence cases skip the LLM call entirely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentRelevanceAgent {

    private final EmbeddingService embeddingService;
    private final OllamaService ollamaService;
    private final DocValidationProperties properties;

    public DocumentRelevanceResult assessRelevance(String claimType, String claimReason,
                                                     Map<String, Object> answers, String ocrText) {

        DocumentTypeClassification classification = classifyDocumentType(ocrText);

        String claimDescription = "Claim Type: " + claimType + "\nClaim Reason: " + claimReason
                + "\nExpected documents relate to: " + claimType + " " + claimReason;

        float[] claimEmbedding = embeddingService.generateEmbedding(claimDescription);
        float[] ocrEmbedding = embeddingService.generateEmbedding(ocrText == null ? "" : ocrText);
        double similarity = cosineSimilarity(claimEmbedding, ocrEmbedding);

        double highThreshold = properties.getRelevance().getHighThreshold();
        double lowThreshold = properties.getRelevance().getLowThreshold();

        if (similarity >= highThreshold) {
            log.info("[DocumentRelevanceAgent] related=true confidence={} source=VECTOR", similarity);
            return DocumentRelevanceResult.builder()
                    .related(true)
                    .documentType(classification.documentType())
                    .confidence(classification.confidence())
                    .similarityScore(similarity)
                    .reason("Semantic similarity " + similarity + " is above the high threshold " + highThreshold)
                    .decisionSource(DocumentRelevanceResult.DecisionSource.VECTOR)
                    .build();
        }

        if (similarity < lowThreshold) {
            log.info("[DocumentRelevanceAgent] related=false confidence={} source=VECTOR", similarity);
            return DocumentRelevanceResult.builder()
                    .related(false)
                    .documentType(classification.documentType())
                    .confidence(classification.confidence())
                    .similarityScore(similarity)
                    .reason("Semantic similarity " + similarity + " is below the low threshold " + lowThreshold)
                    .decisionSource(DocumentRelevanceResult.DecisionSource.VECTOR)
                    .build();
        }

        // Borderline - ask Ollama, per Section 14's decision strategy diagram.
        return assessBorderlineWithOllama(claimType, claimReason, answers, classification, ocrText, similarity);
    }

    private DocumentTypeClassification classifyDocumentType(String ocrText) {
        String prompt = """
                Classify this document's type based on its OCR text. Choose exactly one of:
                MEDICAL_BILL, DISCHARGE_SUMMARY, PRESCRIPTION, LAB_REPORT, MEDICAL_REPORT,
                HOSPITAL_RECEIPT, INSURANCE_DOCUMENT, BANK_STATEMENT, IDENTITY_DOCUMENT,
                VEHICLE_DOCUMENT, UNKNOWN.

                OCR TEXT:
                %s

                Return ONLY valid JSON: {"documentType": "...", "confidence": 0.0}
                Do not output markdown. Return JSON only.
                """.formatted(ocrText == null ? "" : ocrText);

        try {
            return ollamaService.generateStructured(prompt, DocumentTypeClassification.class);
        } catch (OllamaServiceException e) {
            log.warn("[DocumentRelevanceAgent] classification failed ({}), defaulting to UNKNOWN", e.getCode());
            return new DocumentTypeClassification("UNKNOWN", 0.0);
        }
    }

    private DocumentRelevanceResult assessBorderlineWithOllama(String claimType, String claimReason,
                                                                 Map<String, Object> answers,
                                                                 DocumentTypeClassification classification,
                                                                 String ocrText, double similarity) {
        String prompt = """
                You are a Document Relevance Agent for an insurance claim processing system.

                Your task is to determine whether an uploaded document is relevant to the insurance claim.

                You must NOT determine whether the claim should be approved.
                You must NOT determine whether the policy clause is satisfied.
                You ONLY determine whether the document is relevant to the claim.

                CLAIM TYPE:
                %s

                CLAIM REASON:
                %s

                USER PROVIDED ANSWERS:
                %s

                EXPECTED DOCUMENT TYPES:
                MEDICAL_BILL, DISCHARGE_SUMMARY, PRESCRIPTION, LAB_REPORT, MEDICAL_REPORT, HOSPITAL_RECEIPT

                OCR TEXT:
                %s

                Determine:
                1. What type of document is this?
                2. Is this document relevant to the claim?
                3. What evidence from the OCR supports your decision?
                4. Give a confidence score between 0 and 1.

                Return ONLY valid JSON:
                {"related": true, "documentType": "DISCHARGE_SUMMARY", "confidence": 0.95, "reason": "..."}

                Rules:
                - Do not invent information.
                - Base the decision only on supplied OCR text and claim information.
                - If there is insufficient evidence, return related=false or indicate low confidence.
                - Do not approve or reject the insurance claim.
                - Do not determine policy coverage.
                - Do not make medical judgments.
                - Do not output markdown.
                - Return JSON only.
                """.formatted(claimType, claimReason, answers, ocrText == null ? "" : ocrText);

        try {
            OllamaRelevanceDecision decision = ollamaService.generateStructured(prompt, OllamaRelevanceDecision.class);
            log.info("[DocumentRelevanceAgent] related={} confidence={} source=OLLAMA", decision.related(), decision.confidence());
            return DocumentRelevanceResult.builder()
                    .related(decision.related())
                    .documentType(decision.documentType())
                    .confidence(decision.confidence())
                    .similarityScore(similarity)
                    .reason(decision.reason())
                    .decisionSource(DocumentRelevanceResult.DecisionSource.OLLAMA)
                    .build();
        } catch (OllamaServiceException e) {
            // Ollama unavailable on a borderline case - default to unrelated rather
            // than silently letting an unverified document through to validation.
            log.warn("[DocumentRelevanceAgent] Ollama fallback failed ({}), defaulting to unrelated", e.getCode());
            return DocumentRelevanceResult.builder()
                    .related(false)
                    .documentType(classification.documentType())
                    .confidence(0.0)
                    .similarityScore(similarity)
                    .reason("Borderline similarity and Ollama was unavailable: " + e.getCode())
                    .decisionSource(DocumentRelevanceResult.DecisionSource.RULE)
                    .build();
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length && i < b.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
