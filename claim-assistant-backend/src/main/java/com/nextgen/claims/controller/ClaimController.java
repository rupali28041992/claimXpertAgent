package com.nextgen.claims.controller;

import com.nextgen.claims.agent.IntentClassificationAgent;
import com.nextgen.claims.dto.ClaimSubmitRequest;
import com.nextgen.claims.dto.ClaimSubmitResponse;
import com.nextgen.claims.dto.IntentSuggestRequest;
import com.nextgen.claims.dto.IntentSuggestResponse;
import com.nextgen.claims.dto.PolicyLookupResponse;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.rules.ClaimTypeConfig;
import com.nextgen.claims.rules.RulesEngineService;
import com.nextgen.claims.service.ClaimService;
import com.nextgen.claims.service.PolicyService;
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
    private final PolicyService policyService;
    private final ObjectMapper objectMapper;

    /** Screen 1: user types a policy number, we resolve customerId/claimType from Mongo. */
    @GetMapping("/policy/{policyNumber}")
    public PolicyLookupResponse lookupPolicy(@PathVariable String policyNumber) {
        return policyService.lookup(policyNumber);
    }

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
}
