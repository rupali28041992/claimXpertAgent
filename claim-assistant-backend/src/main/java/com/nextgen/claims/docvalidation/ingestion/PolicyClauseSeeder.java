package com.nextgen.claims.docvalidation.ingestion;

import com.nextgen.claims.docvalidation.config.DocValidationProperties;
import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.repository.PolicyClauseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * Seeds Medical_Insurance_Policy_ClaimXpert.pdf into "policy_clauses" on
 * startup, so a fresh local Mongo doesn't need the manual
 * POST /api/docvalidation/admin/policy-ingestion call before PolicyRagAgent
 * has anything to retrieve. Guarded by claimType already having clauses,
 * so restarts never re-ingest/duplicate. Shares docvalidation.ingestion.enabled
 * with PolicyIngestionController - same on/off switch for both the automatic
 * and manual paths.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "docvalidation.ingestion", name = "enabled", matchIfMissing = false)
public class PolicyClauseSeeder implements ApplicationRunner {

    private static final String SEED_CLAIM_TYPE = "MEDICAL";

    private final PolicyClauseIngestor policyClauseIngestor;
    private final PolicyClauseRepository policyClauseRepository;
    private final DocValidationProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!policyClauseRepository.findByClaimType(SEED_CLAIM_TYPE).isEmpty()) {
            log.info("[PolicyClauseSeeder] skipping seed for {} - clauses already present", SEED_CLAIM_TYPE);
            return;
        }

        File pdfFile = new File(properties.getIngestion().getMedicalPolicyPath());
        if (!pdfFile.exists()) {
            log.warn("[PolicyClauseSeeder] seed file not found, skipping: {}", pdfFile.getAbsolutePath());
            return;
        }

        List<PolicyClause> ingested = policyClauseIngestor.ingest(pdfFile, SEED_CLAIM_TYPE, pdfFile.getName());
        log.info("[PolicyClauseSeeder] seeded {} clause(s) for {} from {}",
                ingested.size(), SEED_CLAIM_TYPE, pdfFile.getName());
    }
}
