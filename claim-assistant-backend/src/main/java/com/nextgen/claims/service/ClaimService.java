package com.nextgen.claims.service;

import com.nextgen.claims.agent.ClaimDecisionAgent;
import com.nextgen.claims.agent.ClaimDecisionResult;
import com.nextgen.claims.agent.ValidationAgent;
import com.nextgen.claims.agent.ValidationResult;
import com.nextgen.claims.dto.ClaimSubmitRequest;
import com.nextgen.claims.dto.ClaimSubmitResponse;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.ClaimDocument;
import com.nextgen.claims.model.ClaimStatus;
import com.nextgen.claims.model.StatusChange;
import com.nextgen.claims.repository.ClaimRepository;
import com.nextgen.claims.rules.ReadinessScoreCalculator;
import com.nextgen.claims.rules.RulesEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the single Submit request: Step 1 (generate claimId) through
 * Step 5 (LLM claim decision) from the architecture doc, then does exactly ONE
 * insert into the `claims` collection. Nothing is written to Mongo before
 * this method returns successfully.
 */
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final OcrExtractionService ocrExtractionService;
    private final FileStorageService fileStorageService;
    private final ValidationAgent validationAgent;
    private final RulesEngineService rulesEngineService;
    private final ReadinessScoreCalculator readinessScoreCalculator;
    private final ClaimDecisionAgent claimDecisionAgent;

    public ClaimSubmitResponse submit(ClaimSubmitRequest request, List<MultipartFile> files) {

        // Step 1
        String claimId = "clm_" + UUID.randomUUID().toString().substring(0, 8);

        // Step 2: per file - OCR, cheap checks, then Validation Agent (AI + RAG)
        List<ClaimDocument> documents = new ArrayList<>();
        List<String> fileErrors = new ArrayList<>();

        for (MultipartFile file : files) {
            String cheapCheckError = ocrExtractionService.cheapValidate(file);
            if (cheapCheckError != null) {
                fileErrors.add(file.getOriginalFilename() + ": " + cheapCheckError);
                continue; // Step 2b failure - skip Step 2c for this file
            }

            var extraction = ocrExtractionService.extract(file);
            String fileRef = fileStorageService.store(file);

            ValidationResult validation = validationAgent.validate(
                    request.getClaimType(),
                    request.getClaimReason(),
                    extraction.text(),
                    extraction.fields(),
                    request.getAnswers()
            );

            documents.add(ClaimDocument.builder()
                    .docType(file.getOriginalFilename())
                    .fileRef(fileRef)
                    .ocrText(extraction.text())
                    .extractedFields(extraction.fields())
                    .flags(validation.flags())
                    .clauseSatisfied(validation.clauseSatisfied())
                    .build());
        }

        // Step 3: if any file failed the cheap checks, bounce back now - do NOT write to Mongo
        if (!fileErrors.isEmpty()) {
            return ClaimSubmitResponse.builder()
                    .fileErrors(fileErrors)
                    .build();
        }

        // Step 4: readiness score (plain Java formula, no AI)
        var config = rulesEngineService.getClaimTypeConfig(request.getClaimType());
        int score = readinessScoreCalculator.calculate(documents, config.requiredDocuments().size());
        List<String> flags = readinessScoreCalculator.collectFlags(documents);

        Claim claim = Claim.builder()
                .claimId(claimId)
                .customerId(request.getCustomerId())
                .policyId(request.getPolicyId())
                .claimType(request.getClaimType())
                .claimReason(request.getClaimReason())
                .freeText(request.getFreeText())
                .answers(request.getAnswers())
                .documents(documents)
                .readinessScore(score)
                .flagsAtSubmission(flags)
                .createdAt(Instant.now())
                .build();

        // Step 5: LLM claim decision - retrieved policy clauses + answers + document content
        ClaimDecisionResult decision = claimDecisionAgent.decide(claim);
        ClaimStatus status = toClaimStatus(decision.status());

        claim.setStatus(status);
        claim.setStatusHistory(List.of(
                new StatusChange(ClaimStatus.SUBMITTED, Instant.now(), "Claim submitted", "SYSTEM"),
                new StatusChange(status, Instant.now(), decision.reason(), "SYSTEM")
        ));

        // ONE Mongo write for the entire claim
        claimRepository.save(claim);

        return ClaimSubmitResponse.builder()
                .claimId(claim.getClaimId())
                .readinessScore(score)
                .flags(flags)
                .summary(buildSummary(score, flags))
                .status(claim.getStatus())
                .build();
    }

    public Claim getClaim(String claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
    }

    private ClaimStatus toClaimStatus(ClaimDecisionResult.AutoRoutingStatus status) {
        return ClaimStatus.valueOf(status.name());
    }

    private String buildSummary(int score, List<String> flags) {
        if (flags.isEmpty()) {
            return "Your claim looks complete.";
        }
        return "Your claim looks mostly complete. " + flags.size() + " item(s) flagged - please review before it proceeds.";
    }
}
