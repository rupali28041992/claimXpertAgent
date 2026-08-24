package com.nextgen.claims.docvalidation.agent;

import com.nextgen.claims.docvalidation.config.DocValidationProperties;
import com.nextgen.claims.docvalidation.model.ClaimContext;
import com.nextgen.claims.docvalidation.model.ClaimProcessingStatus;
import com.nextgen.claims.docvalidation.model.ClaimRequest;
import com.nextgen.claims.docvalidation.model.ClaimResult;
import com.nextgen.claims.docvalidation.model.DocumentResult;
import com.nextgen.claims.docvalidation.model.PolicyClause;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimOrchestrator {

    private final DocumentAgent documentAgent;
    private final PolicyRagAgent policyRagAgent;
    private final ClaimDecisionAgent claimDecisionAgent;
    private final DocValidationProperties properties;

    public ClaimResult process(
            ClaimRequest request,
            List<MultipartFile> files) {

        ClaimContext context = createContext(request);

        log.info(
                "[ClaimOrchestrator] START claim={}",
                context.getClaimId()
        );

        // ---------------------------------------------------------
        // STEP 1: Process documents
        // ---------------------------------------------------------

        if (files != null && !files.isEmpty()) {

            for (MultipartFile file : files) {

                DocumentResult document =
                        documentAgent.process(
                                file,
                                context
                        );

                context.addDocument(document);
            }
        }

        // ---------------------------------------------------------
        // STEP 2: Check valid documents
        // ---------------------------------------------------------

        boolean hasValidDocument =
                context.getDocuments() != null
                        && context.getDocuments()
                        .stream()
                        .anyMatch(DocumentResult::isValid);

        if (!hasValidDocument) {

            log.info(
                    "[ClaimOrchestrator] claim={} no valid documents",
                    context.getClaimId()
            );

            context.setDecision(
                    claimDecisionAgent.decide(context)
            );

            context.setStatus(
                    resolveClaimStatus(
                            context.getDocuments()
                    )
            );

            return buildResult(context);
        }

        // ---------------------------------------------------------
        // STEP 3: ONE RAG lookup
        // ---------------------------------------------------------

        int topK =
                properties.getRag().getTopK();

        List<PolicyClause> clauses =
                policyRagAgent.findRelevantClauses(
                        context,
                        topK
                );

        context.setPolicyClauses(clauses);

        log.info(
                "[ClaimOrchestrator] claim={} RAG clauses={}",
                context.getClaimId(),
                clauses.size()
        );

        // ---------------------------------------------------------
        // STEP 4: ONE Ollama decision call
        // ---------------------------------------------------------

        context.setDecision(
                claimDecisionAgent.decide(context)
        );

        // ---------------------------------------------------------
        // STEP 5: Final status
        // ---------------------------------------------------------

        context.setStatus(
                resolveClaimStatus(
                        context.getDocuments()
                )
        );

        log.info(
                "[ClaimOrchestrator] COMPLETE claim={} decision={}",
                context.getClaimId(),
                context.getDecision() == null
                        ? null
                        : context.getDecision().getDecision()
        );

        return buildResult(context);
    }

    private ClaimContext createContext(
            ClaimRequest request) {

        ClaimContext context =
                new ClaimContext();

        context.setClaimId(
                "clm_" +
                        UUID.randomUUID()
                                .toString()
                                .substring(0, 8)
        );

        if (request != null) {

            context.setClaimType(
                    request.getClaimType()
            );

            context.setClaimReason(
                    request.getClaimReason()
            );

            context.setAnswers(
                    request.getAnswers() == null
                            ? Map.of()
                            : request.getAnswers()
            );

        } else {

            context.setAnswers(Map.of());
        }

        context.setStatus(
                ClaimProcessingStatus.RECEIVED
        );

        return context;
    }

    private ClaimProcessingStatus resolveClaimStatus(
            List<DocumentResult> documents) {

        if (documents == null || documents.isEmpty()) {
            return ClaimProcessingStatus.FAILED;
        }

        boolean anySucceeded =
                documents.stream()
                        .anyMatch(DocumentResult::isValid);

        boolean anyFailed =
                documents.stream()
                        .anyMatch(d -> !d.isValid());

        if (anySucceeded && anyFailed) {
            return ClaimProcessingStatus.PARTIALLY_COMPLETED;
        }

        if (anySucceeded) {
            return ClaimProcessingStatus.COMPLETED;
        }

        return ClaimProcessingStatus.FAILED;
    }

    private ClaimResult buildResult(
            ClaimContext context) {

        return ClaimResult.builder()
                .claimId(context.getClaimId())
                .status(context.getStatus())
                .documents(context.getDocuments())
                .decision(context.getDecision())
                .build();
    }
}