package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.model.ValidationResult;
import com.nextgen.claims.docvalidation.service.OllamaService;
import com.nextgen.claims.docvalidation.service.OllamaServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Named DocumentValidationAgent (not ValidationAgent) because
 * com.nextgen.claims.agent.ValidationAgent already exists and is wired
 * into the live /api/claims/submit flow - kept as a distinct class so
 * nothing here collides with or changes that flow.
 *
 * Answers ONLY (Section 24/25/38 of the spec):
 * 1. Does the document satisfy the supplied policy clause?
 * 2. Does the document conflict with the user's answers?
 * It does NOT make a final approve/reject decision.
 *
 * Only called for documents that are valid AND relevant
 * (ClaimOrchestrator's responsibility to filter before calling this).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentValidationAgent {

    private final OllamaService ollamaService;

    public ValidationResult validate(PolicyClause policyClause, String documentType,
                                      String ocrText, Map<String, Object> answers) {

        String clauseText = policyClause == null ? "No specific policy clause was found for this claim." : policyClause.getClauseText();

        String prompt = """
                You are a Policy Document Validation Agent.

                Your responsibility is to compare an insurance document against a specific policy clause and user-provided claim information.

                You must NOT make a final insurance approval decision.

                You must ONLY determine:
                1. Whether the document satisfies the supplied policy clause.
                2. Whether information extracted from the document conflicts with the user's answers.
                3. What specific mismatches or policy issues exist.

                POLICY CLAUSE:
                %s

                DOCUMENT TYPE:
                %s

                OCR TEXT:
                %s

                USER ANSWERS:
                %s

                Return ONLY valid JSON:
                {"clauseSatisfied": true, "flags": [], "confidence": 0.95, "reason": "..."}

                Possible flags include:
                "mismatch:hospitalName", "mismatch:patientName", "mismatch:admissionDate",
                "mismatch:dischargeDate", "policy:waitingPeriod", "missing:requiredInformation",
                "insufficientEvidence"

                Rules:
                - Do not invent facts.
                - Do not assume missing information.
                - If the document does not contain enough evidence, use "insufficientEvidence".
                - Distinguish missing information from contradictory information.
                - A mismatch means the document says one thing while the user answer says another.
                - Do not make a final claim approval/rejection decision.
                - Do not provide medical advice.
                - Return JSON only.
                """.formatted(clauseText, documentType, ocrText == null ? "" : ocrText, answers);

        try {
            return ollamaService.generateStructured(prompt, ValidationResult.class);
        } catch (OllamaServiceException e) {
            log.warn("[DocumentValidationAgent] validation failed ({}), flagging insufficientEvidence", e.getCode());
            return ValidationResult.builder()
                    .clauseSatisfied(false)
                    .flags(List.of("insufficientEvidence"))
                    .confidence(0.0)
                    .reason("Validation could not be completed: " + e.getCode())
                    .build();
        }
    }
}
