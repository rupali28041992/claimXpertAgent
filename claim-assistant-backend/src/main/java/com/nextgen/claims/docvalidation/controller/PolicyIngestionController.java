package com.nextgen.claims.docvalidation.controller;

import com.nextgen.claims.docvalidation.config.DocValidationProperties;
import com.nextgen.claims.docvalidation.ingestion.PolicyClauseIngestor;
import com.nextgen.claims.docvalidation.model.PolicyClause;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Manual, one-time policy-document ingestion (Section 21/31 of the spec's
 * RAG store setup) - NOT part of the claim-submission path, and disabled
 * by default (docvalidation.ingestion.enabled=false). This project has no
 * Spring Security dependency, so leaving this off by default is the
 * cheapest real control for what's meant to be a developer/admin-triggered
 * one-time action rather than a public endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/docvalidation/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "docvalidation.ingestion", name = "enabled", matchIfMissing = false)
public class PolicyIngestionController {

    private final PolicyClauseIngestor policyClauseIngestor;
    private final DocValidationProperties properties;

    @PostMapping("/policy-ingestion")
    public Map<String, Object> ingest(@RequestParam(defaultValue = "MEDICAL") String claimType) {
        File pdfFile = new File(properties.getIngestion().getMedicalPolicyPath());
        log.warn("[PolicyIngestionController] manual policy ingestion triggered claimType={} file={}",
                claimType, pdfFile.getAbsolutePath());

        List<PolicyClause> ingested = policyClauseIngestor.ingest(pdfFile, claimType, pdfFile.getName());

        return Map.of(
                "claimType", claimType,
                "sourceDocument", pdfFile.getName(),
                "clausesIngested", ingested.size(),
                "sectionTitles", ingested.stream().map(PolicyClause::getClaimReason).toList()
        );
    }
}
