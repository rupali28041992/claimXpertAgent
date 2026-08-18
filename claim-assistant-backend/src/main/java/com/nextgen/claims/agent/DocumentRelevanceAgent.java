package com.nextgen.claims.agent;

import com.nextgen.claims.dto.DocumentRelevanceResult;
import com.nextgen.claims.service.EmbeddingService;
import com.nextgen.claims.service.OllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Answers ONE question: "is this uploaded document actually about this
 * claim?" - never approves/rejects, never checks policy coverage (that's
 * PolicyRagAgent + ValidationAgent's job - see rule 38/49).
 *
 * Hybrid pipeline (rule 14): a cheap keyword classifier always runs first to
 * label the document type; embedding cosine-similarity against a semantic
 * description of the claim then makes the RELATED/UNRELATED call for the
 * common cases, and only "borderline" similarity scores fall through to an
 * Ollama call. Most documents never reach Ollama.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentRelevanceAgent {

    private final EmbeddingService embeddingService;
    private final OllamaService ollamaService;

    @Value("${relevance.high-threshold:0.80}")
    private double highThreshold;

    @Value("${relevance.low-threshold:0.60}")
    private double lowThreshold;

    private static final Map<String, List<String>> EXPECTED_DOCUMENTS = Map.of(
            "HEALTH", List.of("hospital bill", "discharge summary", "medical report", "prescription", "lab report"),
            "MEDICAL", List.of("hospital bill", "discharge summary", "medical report", "prescription", "lab report"),
            "TRAVEL", List.of("flight ticket", "boarding pass", "baggage report", "medical certificate"),
            "MOTOR", List.of("repair estimate", "photo evidence", "police report", "registration certificate"),
            "LIFE", List.of("death certificate", "medical records", "policy document")
    );

    private static final Map<String, List<String>> TYPE_KEYWORDS = Map.ofEntries(
            Map.entry("DISCHARGE_SUMMARY", List.of("discharge summary", "date of admission", "date of discharge")),
            Map.entry("MEDICAL_BILL", List.of("invoice", "bill no", "amount payable", "hospital bill")),
            Map.entry("PRESCRIPTION", List.of("prescription", "rx", "dosage", "tablet")),
            Map.entry("LAB_REPORT", List.of("lab report", "test result", "specimen", "pathology")),
            Map.entry("HOSPITAL_RECEIPT", List.of("receipt", "payment received")),
            Map.entry("INSURANCE_DOCUMENT", List.of("insurance policy", "policy number", "premium", "sum insured")),
            Map.entry("BANK_STATEMENT", List.of("account statement", "ifsc", "transaction")),
            Map.entry("IDENTITY_DOCUMENT", List.of("passport", "aadhar", "driving licence", "date of birth")),
            Map.entry("VEHICLE_DOCUMENT", List.of("vehicle", "registration number", "engine number", "chassis"))
    );

    public DocumentRelevanceResult evaluate(String claimType, String claimReason,
                                             Map<String, Object> answers, String ocrText) {

        String documentType = classifyByKeywords(ocrText);

        float[] claimEmbedding = embeddingService.generateEmbedding(buildClaimDescription(claimType, claimReason));
        float[] ocrEmbedding = embeddingService.generateEmbedding(truncate(ocrText, 4000));
        double similarity = embeddingService.cosineSimilarity(claimEmbedding, ocrEmbedding);

        if (similarity >= highThreshold) {
            log.info("agent=DocumentRelevanceAgent related=true confidence={} source=VECTOR", round(similarity));
            return DocumentRelevanceResult.builder()
                    .related(true).documentType(documentType).confidence(similarity)
                    .similarityScore(similarity).decisionSource("VECTOR")
                    .reason("Embedding similarity " + round(similarity) + " is at/above the high threshold.")
                    .build();
        }
        if (similarity < lowThreshold) {
            log.info("agent=DocumentRelevanceAgent related=false confidence={} source=VECTOR", round(1 - similarity));
            return DocumentRelevanceResult.builder()
                    .related(false).documentType(documentType).confidence(1 - similarity)
                    .similarityScore(similarity).decisionSource("VECTOR")
                    .reason("Embedding similarity " + round(similarity) + " is below the low threshold.")
                    .build();
        }

        return borderlineWithOllama(claimType, claimReason, answers, documentType, ocrText, similarity);
    }

    private DocumentRelevanceResult borderlineWithOllama(String claimType, String claimReason,
                                                           Map<String, Object> answers, String documentType,
                                                           String ocrText, double similarity) {
        String prompt = buildRelevancePrompt(claimType, claimReason, answers, documentType, ocrText);
        try {
            OllamaRelevanceResponse resp = ollamaService.generateStructured(prompt, OllamaRelevanceResponse.class);
            log.info("agent=DocumentRelevanceAgent related={} confidence={} source=OLLAMA",
                    resp.related(), resp.confidence());
            return DocumentRelevanceResult.builder()
                    .related(resp.related())
                    .documentType(resp.documentType() != null ? resp.documentType() : documentType)
                    .confidence(resp.confidence())
                    .similarityScore(similarity)
                    .decisionSource("OLLAMA")
                    .reason(resp.reason())
                    .build();
        } catch (OllamaService.OllamaException e) {
            // Ollama unavailable/timeout/bad JSON - degrade gracefully instead of failing the document.
            boolean fallbackRelated = similarity >= ((highThreshold + lowThreshold) / 2);
            log.warn("agent=DocumentRelevanceAgent status=OLLAMA_FALLBACK errorCode={} fallbackRelated={}",
                    e.getErrorCode(), fallbackRelated);
            return DocumentRelevanceResult.builder()
                    .related(fallbackRelated).documentType(documentType)
                    .confidence(similarity).similarityScore(similarity)
                    .decisionSource("VECTOR")
                    .reason("Borderline similarity; Ollama unavailable (" + e.getErrorCode()
                            + "), fell back to the similarity midpoint.")
                    .build();
        }
    }

    private String classifyByKeywords(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return "UNKNOWN";
        }
        String lower = ocrText.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : TYPE_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return "UNKNOWN";
    }

    private String buildClaimDescription(String claimType, String claimReason) {
        List<String> expected = EXPECTED_DOCUMENTS.getOrDefault(
                claimType == null ? "" : claimType.toUpperCase(Locale.ROOT),
                List.of("supporting document", "claim form"));
        return "Claim Type: " + claimType + "\nClaim Reason: " + claimReason
                + "\nExpected documents: " + String.join(", ", expected);
    }

    private String buildRelevancePrompt(String claimType, String claimReason, Map<String, Object> answers,
                                         String documentType, String ocrText) {
        List<String> expected = EXPECTED_DOCUMENTS.getOrDefault(
                claimType == null ? "" : claimType.toUpperCase(Locale.ROOT),
                List.of("supporting document", "claim form"));

        return """
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
                %s

                OCR TEXT:
                %s

                Determine:
                1. What type of document is this?
                2. Is this document relevant to the claim?
                3. What evidence from the OCR supports your decision?
                4. Give a confidence score between 0 and 1.

                Rules:
                - Do not invent information.
                - Base the decision only on supplied OCR text and claim information.
                - If there is insufficient evidence, return related=false or indicate low confidence.
                - Do not approve or reject the insurance claim.
                - Do not determine policy coverage.
                - Do not make medical judgments.
                - Do not output markdown.
                - Return JSON only, matching this exact structure - no extra text:
                {
                  "related": true,
                  "documentType": "%s",
                  "confidence": 0.0,
                  "reason": "..."
                }
                """.formatted(claimType, claimReason, formatAnswers(answers),
                String.join(", ", expected), truncate(ocrText, 4000), documentType);
    }

    /**
     * Never returns a Map's raw toString() - an empty map's "{}" (or any "{...}") baked
     * into a prompt string via String.formatted() gets misparsed by Spring AI's template
     * engine before the request ever reaches Ollama (the same failure mode ValidationAgent's
     * .param()-based prompt building avoids). Plain "key: value" lines are always safe.
     */
    private String formatAnswers(Map<String, Object> answers) {
        if (answers == null || answers.isEmpty()) {
            return "(none)";
        }
        return answers.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    public record OllamaRelevanceResponse(boolean related, String documentType, double confidence, String reason) {
    }
}
