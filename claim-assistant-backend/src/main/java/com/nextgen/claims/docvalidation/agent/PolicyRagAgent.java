package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.DocumentEvidence;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.service.EmbeddingService;
import com.nextgen.claims.docvalidation.service.MongoVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyRagAgent {

    private final EmbeddingService embeddingService;
    private final MongoVectorService mongoVectorService;

    /**
     * Performs ONE RAG retrieval for the entire claim.
     *
     * IMPORTANT:
     * Raw OCR is NEVER sent to the embedding model.
     *
     * Only:
     * - claim type
     * - claim reason
     * - user answers
     * - structured document evidence
     *
     * are used to build the semantic query.
     */
    public List<PolicyClause> findRelevantClauses(
            ClaimContext context,
            int topK) {

        String query = buildClaimQuery(context);

        log.info(
                "[PolicyRagAgent] claim={} queryChars={}",
                context.getClaimId(),
                query.length()
        );

        float[] queryEmbedding =
                embeddingService.generateEmbedding(query);

        List<PolicyClause> clauses =
                mongoVectorService.findTopKClauses(
                        queryEmbedding,
                        context.getClaimType(),
                        topK
                );

        if (clauses.isEmpty()) {

            log.info(
                    "[PolicyRagAgent] No relevant clauses found claim={}",
                    context.getClaimId()
            );

            return List.of();
        }

        String summary = clauses.stream()
                .map(c ->
                        c.getClaimReason()
                                + " (similarity candidate)"
                )
                .collect(Collectors.joining("; "));

        log.info(
                "[PolicyRagAgent] Retrieved {} clauses claim={} : {}",
                clauses.size(),
                context.getClaimId(),
                summary
        );

        return clauses;
    }

    private String buildClaimQuery(
            ClaimContext context) {

        StringBuilder query =
                new StringBuilder();

        query.append("Claim Type: ")
                .append(nullSafe(context.getClaimType()))
                .append("\n");

        query.append("Claim Reason: ")
                .append(nullSafe(context.getClaimReason()))
                .append("\n");

        /*
         * User answers.
         */
        if (context.getAnswers() != null
                && !context.getAnswers().isEmpty()) {

            query.append("\nUser Answers:\n");

            for (Map.Entry<String, Object> entry :
                    context.getAnswers().entrySet()) {

                query.append(entry.getKey())
                        .append(": ")
                        .append(String.valueOf(entry.getValue()))
                        .append("\n");
            }
        }

        /*
         * STRUCTURED document evidence.
         *
         * NEVER use document.getOcrText() here.
         */
        if (context.getDocuments() != null
                && !context.getDocuments().isEmpty()) {

            query.append("\nClaim Document Facts:\n");

            for (DocumentResult document :
                    context.getDocuments()) {

                if (!document.isValid()) {
                    continue;
                }

                DocumentEvidence evidence =
                        document.getEvidence();

                if (evidence == null) {
                    continue;
                }

                appendEvidence(query, evidence);
            }
        }

        return query.toString().trim();
    }

    private void appendEvidence(
            StringBuilder query,
            DocumentEvidence evidence) {

        appendIfPresent(
                query,
                "Document Type",
                evidence.getDocumentType()
        );

        appendIfPresent(
                query,
                "Patient Name",
                evidence.getPatientName()
        );

        appendIfPresent(
                query,
                "Hospital",
                evidence.getHospitalName()
        );

        appendIfPresent(
                query,
                "Diagnosis",
                evidence.getDiagnosis()
        );

        appendIfPresent(
                query,
                "Admission Date",
                evidence.getAdmissionDate()
        );

        appendIfPresent(
                query,
                "Discharge Date",
                evidence.getDischargeDate()
        );

        appendIfPresent(
                query,
                "Treatment",
                evidence.getTreatment()
        );

        appendIfPresent(
                query,
                "Bill Amount",
                evidence.getBillAmount()
        );

        appendIfPresent(
                query,
                "Policy Number",
                evidence.getPolicyNumber()
        );

        appendIfPresent(
                query,
                "Claim Number",
                evidence.getClaimNumber()
        );

        query.append("\n");
    }

    private void appendIfPresent(
            StringBuilder query,
            String label,
            String value) {

        if (value != null && !value.isBlank()) {

            query.append(label)
                    .append(": ")
                    .append(value.trim())
                    .append("\n");
        }
    }

    private String nullSafe(String value) {

        return value == null ? "" : value;
    }
}