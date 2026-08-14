package com.nextgen.claims.docvalidation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgen.claims.docvalidation.agent.ClaimOrchestrator;
import com.nextgen.claims.docvalidation.exception.ClaimException;
import com.nextgen.claims.docvalidation.model.ClaimRequest;
import com.nextgen.claims.docvalidation.model.ClaimResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
public class DocumentValidationClaimController {

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
