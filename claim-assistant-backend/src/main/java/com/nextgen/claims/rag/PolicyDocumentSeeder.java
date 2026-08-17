package com.nextgen.claims.rag;

import com.nextgen.claims.repository.PolicyClauseVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Seeds the two policy documents that ship as classpath resources into policy_clause_vectors
 * on startup. Guarded per claimType so restarts don't re-ingest and duplicate rows every boot.
 * Admin-uploaded documents (MOTOR/LIFE, or updated versions) go through
 * {@code PolicyDocumentController} instead - this runner only ever handles these two files.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyDocumentSeeder implements ApplicationRunner {

    private static final Map<String, String> SEED_FILES = Map.of(
            "MEDICAL", "medical_claims_policy.pdf",
            "TRAVEL", "travel_claims_policy.pdf"
    );

    private final PolicyClauseVectorRepository repository;
    private final PolicyDocumentIngestionService ingestionService;

    @Override
    public void run(ApplicationArguments args) {
        SEED_FILES.forEach((claimType, filename) -> {
            if (!repository.findByProductType(claimType).isEmpty()) {
                log.info("Skipping policy seed for {} - clauses already present", claimType);
                return;
            }
            try (InputStream content = new ClassPathResource(filename).getInputStream()) {
                int chunkCount = ingestionService.ingest(claimType, null, content, filename);
                log.info("Seeded {} policy clause chunk(s) for {} from {}", chunkCount, claimType, filename);
            } catch (IOException e) {
                log.error("Failed to seed policy document {} for {}", filename, claimType, e);
            }
        });
    }
}
