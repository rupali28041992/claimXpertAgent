package com.nextgen.claims.docvalidation.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Deterministic, keyword-based relevance check - no AI/Ollama call. Catches
 * documents that are obviously the wrong type for the claim (e.g. a travel
 * boarding pass uploaded against a MEDICAL claim) right after OCR, so
 * DocumentAgent can fail that document before ClaimOrchestrator ever spends
 * an Ollama call (RAG embedding or the decision prompt) on it.
 */
@Service
public class DocumentRelevanceService {

    private static final Map<String, List<String>> CLAIM_TYPE_KEYWORDS = Map.of(
            "MEDICAL", List.of("hospital", "discharge", "diagnosis", "admission", "patient", "physician", "doctor", "treatment"),
            "MOTOR", List.of("vehicle", "accident", "rc", "driving license", "repair", "collision", "insurance", "damage"),
            "TRAVEL", List.of("flight", "boarding pass", "airline", "trip", "delay", "baggage", "itinerary"),
            "LIFE", List.of("death certificate", "deceased", "nominee", "cause of death", "demise")
    );

    /**
     * True when the OCR text contains at least one keyword expected for this
     * claim type. Permissive (returns true) for blank OCR text - e.g. image
     * uploads, where PdfTextOcrService has no real OCR wired in - and for
     * claim types with no keyword list, since there is nothing reliable to
     * check against in either case.
     */
    public boolean isRelevant(String claimType, String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return true;
        }
        List<String> keywords = CLAIM_TYPE_KEYWORDS.get(claimType == null ? null : claimType.toUpperCase());
        if (keywords == null) {
            return true;
        }
        String lower = ocrText.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }
}
