package com.nextgen.claims.rag;

import com.nextgen.claims.config.PolicySeedProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Seeds policy_clause_vectors from the policy PDFs bundled under
 * src/main/resources, once per productType (skips if that productType
 * already has clauses), by delegating to the same chunking/embedding logic
 * PdfIngestionService already uses for admin-triggered uploads
 * (POST /api/admin/ingest-pdf) - single source of truth for turning a policy
 * PDF into clauses, instead of a second, weaker regex living here too.
 *
 * The productType -> filename mapping comes from claims.policy-seed.pdfs in
 * application.yml (PolicySeedProperties), not a hardcoded Java map - adding a
 * new product's policy PDF is a config change, not a code change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyClauseSeeder implements ApplicationRunner {

    private final PolicySeedProperties policySeedProperties;
    private final PdfIngestionService pdfIngestionService;

    @Override
    public void run(ApplicationArguments args) {
        if (!policySeedProperties.isEnabled()) {
            log.info("PolicyClauseSeeder disabled (claims.policy-seed.enabled=false) - skipping.");
            return;
        }
        policySeedProperties.getPdfs().forEach(this::seedProductType);
    }

    private void seedProductType(String productType, String resourceName) {
        try (InputStream inputStream = new ClassPathResource(resourceName).getInputStream()) {
            int count = pdfIngestionService.ingest(inputStream, productType, false);
            if (count > 0) {
                log.info("Seeded {} policy clause(s) for productType={} from {}", count, productType, resourceName);
            }
        } catch (Exception e) {
            // One product type's PDF failing (e.g. Ollama not up yet) must not fail app
            // startup or block the other product type from seeding.
            log.warn("Failed to seed productType={} from {}: {}", productType, resourceName, e.getMessage());
        }
    }
}
