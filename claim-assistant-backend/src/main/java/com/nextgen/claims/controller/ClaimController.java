package com.nextgen.claims.controller;

import com.nextgen.claims.docvalidation.agent.ClaimOrchestrator;
import com.nextgen.claims.docvalidation.model.ClaimEntity;
import com.nextgen.claims.docvalidation.model.ClaimRequest;
import com.nextgen.claims.docvalidation.model.ClaimResult;
import com.nextgen.claims.docvalidation.repository.ClaimEntityRepository;
import com.nextgen.claims.dto.ClaimSubmitRequest;
import com.nextgen.claims.model.ClaimAnswer;
import com.nextgen.claims.rules.ClaimTypeConfig;
import com.nextgen.claims.rules.RulesEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend entry points for the Angular wizard. /submit now runs entirely
 * through the docvalidation pipeline (OCR -> relevance -> top-K RAG ->
 * ClaimDecisionAgent) - there is no separate GoRules-driven readiness
 * score or routing decision anymore, and no separate
 * POST /api/docvalidation/claims endpoint; this is the one submit path.
 */
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class ClaimController {

    private final ClaimOrchestrator claimOrchestrator;
    private final ClaimEntityRepository claimEntityRepository;
    private final RulesEngineService rulesEngineService;
    private final ObjectMapper objectMapper;

    /** Screens 3 & 4 pull their field/document lists from this (GoRules-backed) lookup - a static config lookup, not a decision. */
    @GetMapping("/config/{claimType}")
    public ClaimTypeConfig getConfig(@PathVariable String claimType) {
        return rulesEngineService.getClaimTypeConfig(claimType);
    }

    /**
     * The single Submit call from Screen 4. Multipart: "claim" part is the
     * JSON body (ClaimSubmitRequest), "files" parts are the uploaded documents.
     * Delegates straight to ClaimOrchestrator - Ollama makes the final
     * APPROVED/REJECTED/MANUAL_REVIEW call, not a GoRules table.
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClaimResult submit(@RequestPart("claim") String claimJson,
                               @RequestPart("files") List<MultipartFile> files) throws Exception {
        ClaimSubmitRequest submitRequest = objectMapper.readValue(claimJson, ClaimSubmitRequest.class);
        ClaimRequest request = toClaimRequest(submitRequest);
        return claimOrchestrator.process(request, files);
    }

    /** Screen 6 - track claim status. */
    @GetMapping("/{claimId}")
    public ClaimEntity getClaim(@PathVariable String claimId) {
        return claimEntityRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
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
        return request;
    }
}
