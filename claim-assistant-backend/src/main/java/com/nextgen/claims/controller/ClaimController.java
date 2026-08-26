package com.nextgen.claims.controller;

import com.nextgen.claims.docvalidation.agent.ClaimOrchestrator;
import com.nextgen.claims.docvalidation.agent.ClaimStatusAgent;
import com.nextgen.claims.docvalidation.model.ClaimEntity;
import com.nextgen.claims.docvalidation.model.ClaimProcessingStatus;
import com.nextgen.claims.docvalidation.model.ClaimRequest;
import com.nextgen.claims.docvalidation.model.ClaimResult;
import com.nextgen.claims.docvalidation.repository.ClaimEntityRepository;
import com.nextgen.claims.dto.ClaimSubmitRequest;
import com.nextgen.claims.dto.PolicyLookupResponse;
import com.nextgen.claims.dto.QuestionnaireRequest;
import com.nextgen.claims.dto.QuestionnaireState;
import com.nextgen.claims.model.ClaimAnswer;
import com.nextgen.claims.rules.ClaimTypeConfig;
import com.nextgen.claims.rules.RulesEngineService;
import com.nextgen.claims.service.PolicyService;
import com.nextgen.claims.util.ByteArrayMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class ClaimController {

    private final ClaimOrchestrator claimOrchestrator;
    private final ClaimEntityRepository claimEntityRepository;
    private final ClaimStatusAgent claimStatusAgent;
    private final RulesEngineService rulesEngineService;
    private final PolicyService policyService;
    private final ObjectMapper objectMapper;

    @GetMapping("/policy/{policyNumber}")
    public ResponseEntity<PolicyLookupResponse> lookupPolicy(@PathVariable String policyNumber) {
        try {
            return ResponseEntity.ok(policyService.lookup(policyNumber));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/questions")
    public QuestionnaireState getNextQuestions(@RequestBody QuestionnaireRequest request) {
        Map<String, String> answers = request.getAnswers() != null ? request.getAnswers() : Map.of();
        return rulesEngineService.evaluateQuestions(answers);
    }

    @GetMapping("/config/{claimType}")
    public ClaimTypeConfig getConfig(@PathVariable String claimType) {
        return rulesEngineService.getClaimTypeConfig(claimType);
    }

    /**
     * Returns 202 Accepted immediately. Pipeline runs async — poll GET /{claimId} for result.
     * File bytes are copied before dispatch so multipart temp files can be safely released.
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClaimResult> submit(
            @RequestPart("claim") String claimJson,
            @RequestPart("files") List<MultipartFile> files) throws Exception {

        ClaimSubmitRequest submitRequest = objectMapper.readValue(claimJson, ClaimSubmitRequest.class);
        ClaimRequest request = toClaimRequest(submitRequest);

        long claimNum = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        String claimId = String.format("CLM%06d", claimNum);

        // Copy bytes before returning — multipart temp files may be cleaned up after 202 response
        List<MultipartFile> copiedFiles = files.stream()
                .map(f -> {
                    try { return (MultipartFile) new ByteArrayMultipartFile(f); }
                    catch (Exception e) { throw new RuntimeException("Failed to copy file: " + f.getOriginalFilename(), e); }
                })
                .toList();

        // Persist initial RECEIVED state so GET /{claimId} works immediately
        claimEntityRepository.save(ClaimEntity.builder()
                .claimId(claimId)
                .customerId(submitRequest.getCustomerId())
                .claimType(request.getClaimType())
                .claimReason(request.getClaimReason())
                .answers(request.getAnswers())
                .status(ClaimProcessingStatus.RECEIVED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        claimOrchestrator.processAsync(claimId, request, copiedFiles);

        log.info("[ClaimController] claim={} submitted async files={}", claimId, copiedFiles.size());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ClaimResult.builder()
                        .claimId(claimId)
                        .status(ClaimProcessingStatus.RECEIVED)
                        .build());
    }

    @GetMapping
    public List<ClaimEntity> getAllClaims() {
        return claimStatusAgent.getAllClaims();
    }

    @GetMapping("/by-customer/{customerId}")
    public List<ClaimEntity> getClaimsByCustomer(@PathVariable String customerId) {
        return claimEntityRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimEntity> getClaim(@PathVariable String claimId) {
        return claimStatusAgent.getClaimById(claimId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private ClaimRequest toClaimRequest(ClaimSubmitRequest submitRequest) {
        Map<String, Object> answers = new HashMap<>();
        if (submitRequest.getAnswers() != null) {
            for (ClaimAnswer answer : submitRequest.getAnswers()) {
                answers.put(answer.getQuestionId(), answer.getAnswerText());
            }
        }
        ClaimRequest request = new ClaimRequest();
        request.setClaimType(submitRequest.getClaimType());
        request.setClaimReason(submitRequest.getClaimReason());
        request.setAnswers(answers);
        request.setFileDocumentTypes(
                submitRequest.getFileDocumentTypes() != null
                        ? submitRequest.getFileDocumentTypes()
                        : Map.of());
        return request;
    }
}
