package com.nextgen.claims.controller;

import com.nextgen.claims.agent.IntentClassificationAgent;
import com.nextgen.claims.docvalidation.agent.ClaimOrchestrator;
import com.nextgen.claims.docvalidation.exception.ClaimException;
import com.nextgen.claims.docvalidation.model.ClaimRequest;
import com.nextgen.claims.docvalidation.model.ClaimResult;
import com.nextgen.claims.dto.ClaimSubmitRequest;
import com.nextgen.claims.dto.ClaimSubmitResponse;
import com.nextgen.claims.dto.IntentSuggestRequest;
import com.nextgen.claims.dto.IntentSuggestResponse;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.rules.ClaimTypeConfig;
import com.nextgen.claims.rules.RulesEngineService;
import com.nextgen.claims.service.ClaimService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Backend entry points for the Angular wizard. Screens 1-4 collect data
 * client-side (see Angular integration notes) and call these endpoints;
 * only /submit touches Mongo.
 */
@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class ClaimController {

    private final ClaimService claimService;
    private final IntentClassificationAgent intentClassificationAgent;
    private final RulesEngineService rulesEngineService;
    private final ObjectMapper objectMapper;

    /** Screen 1's optional "Suggest Claim Type" button. No RAG. */
    @PostMapping("/suggest-type")
    public IntentSuggestResponse suggestType(@RequestBody IntentSuggestRequest request) {
        return intentClassificationAgent.suggest(request.getFreeText());
    }

    /** Screens 3 & 4 pull their field/document lists from this (GoRules-backed) lookup. */
    @GetMapping("/config/{claimType}")
    public ClaimTypeConfig getConfig(@PathVariable String claimType) {
        return rulesEngineService.getClaimTypeConfig(claimType);
    }

    /**
     * The single Submit call from Screen 4. Multipart: "claim" part is the
     * JSON body (ClaimSubmitRequest), "files" parts are the uploaded documents.
     */
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClaimSubmitResponse submit(@RequestPart("claim") String claimJson,
                                       @RequestPart("files") List<MultipartFile> files) throws Exception {
        ClaimSubmitRequest request = objectMapper.readValue(claimJson, ClaimSubmitRequest.class);
        return claimService.submit(request, files);
    }

    /** Screen 6 - track claim status. */
    @GetMapping("/{claimId}")
    public Claim getClaim(@PathVariable String claimId) {
        return claimService.getClaim(claimId);
    }

    /**
     * New endpoint for the multi-agent document validation pipeline
     * (Section 7 of the spec): POST /api/docvalidation/claims. Deliberately a
     * different path from the existing POST /api/claims/submit
     * (com.nextgen.claims.controller.ClaimController) so that flow is
     * completely untouched by this module.
     */
    @RestController
    @RequestMapping("/api/docvalidation/claims")
    @RequiredArgsConstructor
    @CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
    public static class DocumentValidationClaimController {

        private final ClaimOrchestrator claimOrchestrator;
        private final ObjectMapper objectMapper;

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ClaimResult submitClaim(@RequestPart("claim") String claimJson,
                                       @RequestPart("files") List<MultipartFile> files) {
            ClaimRequest request;
            try {
                request = objectMapper.readValue(claimJson, ClaimRequest.class);
            } catch (Exception e) {
                throw new ClaimException("Invalid claim JSON payload", e);
            }
            return claimOrchestrator.process(request, files);
        }
    }
}
