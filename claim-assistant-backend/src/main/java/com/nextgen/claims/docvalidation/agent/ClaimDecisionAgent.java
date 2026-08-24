package com.nextgen.claims.docvalidation.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.ClaimDecisionResult;
import com.nextgen.claims.docvalidation.model.ClaimDecisionStatus;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.service.OllamaService;
import com.nextgen.claims.docvalidation.service.OllamaServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimDecisionAgent {

    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;

    public ClaimDecisionResult decide(ClaimContext context) {

        List<DocumentResult> validDocuments =
                context.getDocuments()
                        .stream()
                        .filter(DocumentResult::isValid)
                        .filter(d -> d.getEvidence() != null)
                        .toList();

        List<PolicyClause> clauses =
                context.getPolicyClauses() == null
                        ? List.of()
                        : context.getPolicyClauses();

        // ---------------------------------------------------------
        // Short circuit
        // ---------------------------------------------------------

        if (validDocuments.isEmpty()) {

            log.info(
                    "[ClaimDecisionAgent] claim={} no valid evidence",
                    context.getClaimId()
            );

            return logAndReturn(
                    context,
                    manualReview(
                            "No valid relevant document evidence was extracted."
                    )
            );
        }

        if (clauses.isEmpty()) {

            log.info(
                    "[ClaimDecisionAgent] claim={} no policy clauses",
                    context.getClaimId()
            );

            return logAndReturn(
                    context,
                    manualReview(
                            "No applicable policy clause was retrieved."
                    )
            );
        }

        // ---------------------------------------------------------
        // Build SMALL prompt
        // ---------------------------------------------------------

        String prompt =
                buildPrompt(
                        context,
                        validDocuments,
                        clauses
                );

        log.info(
                "[ClaimDecisionAgent] claim={} promptChars={} documents={} clauses={}",
                context.getClaimId(),
                prompt.length(),
                validDocuments.size(),
                clauses.size()
        );

        try {

            ClaimDecisionResult result =
                    ollamaService.generateStructured(
                            prompt,
                            ClaimDecisionResult.class
                    );

            if (result == null) {

                return logAndReturn(
                        context,
                        manualReview(
                                "Ollama returned no decision."
                        )
                );
            }

            return logAndReturn(context, result);

        } catch (OllamaServiceException e) {

            log.warn(
                    "[ClaimDecisionAgent] claim={} decision failed code={}",
                    context.getClaimId(),
                    e.getCode()
            );

            return logAndReturn(
                    context,
                    manualReview(
                            "Decision could not be completed: "
                                    + e.getCode()
                    )
            );
        }
    }

    private String buildPrompt(
            ClaimContext context,
            List<DocumentResult> documents,
            List<PolicyClause> clauses) {

        String evidenceJson;

        String clausesJson;

        try {

            evidenceJson =
                    objectMapper.writeValueAsString(
                            documents.stream()
                                    .map(DocumentResult::getEvidence)
                                    .toList()
                    );

            clausesJson =
                    objectMapper.writeValueAsString(
                            clauses
                    );

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "Unable to create Ollama prompt",
                    e
            );
        }

        return """
                You are an insurance claim decision engine.

                Decide exactly one:
                APPROVED
                REJECTED
                MANUAL_REVIEW

                Use ONLY the supplied claim evidence and policy clauses.

                APPROVED:
                Evidence supports the claim and satisfies the applicable policy.

                REJECTED:
                A supplied policy clause clearly excludes or disqualifies the claim.

                MANUAL_REVIEW:
                Evidence is insufficient, conflicting, or policy applicability is unclear.

                Do not invent facts.
                Do not repeat the evidence.
                Do not provide analysis outside JSON.

                CLAIM TYPE:
                %s

                CLAIM REASON:
                %s

                USER ANSWERS:
                %s

                DOCUMENT EVIDENCE:
                %s

                RELEVANT POLICY CLAUSES:
                %s

                Return ONLY this JSON structure:
                {
                  "decision": "APPROVED",
                  "conditions": [],
                  "matchedClauses": [],
                  "confidence": 0.95,
                  "reason": "Short reason"
                }
                """
                .formatted(
                        context.getClaimType(),
                        context.getClaimReason(),
                        context.getAnswers(),
                        evidenceJson,
                        clausesJson
                );
    }

    private ClaimDecisionResult manualReview(String reason) {

        return ClaimDecisionResult.builder()
                .decision(ClaimDecisionStatus.MANUAL_REVIEW)
                .conditions(List.of())
                .matchedClauses(List.of())
                .confidence(0.0)
                .reason(reason)
                .build();
    }

    private ClaimDecisionResult logAndReturn(
            ClaimContext context,
            ClaimDecisionResult result) {

        log.info(
                "[ClaimDecisionAgent] claim={} decision={} confidence={} reason={}",
                context.getClaimId(),
                result.getDecision(),
                result.getConfidence(),
                result.getReason()
        );

        return result;
    }
}