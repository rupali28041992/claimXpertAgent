package com.nextgen.claims.rag;

import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.repository.PolicyClauseVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Seeds policy_clause_vectors from the sample policy PDFs bundled under
 * src/main/resources: each "Section N: ..." block (required documents +
 * eligibility rules) becomes one PolicyClauseVector. Skipped per productType
 * once it already has clauses, so restarts don't re-embed/duplicate them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyClauseSeeder implements ApplicationRunner {

    private static final Map<String, String> POLICY_PDFS_BY_PRODUCT_TYPE = Map.of(
            "MEDICAL", "medical_claims_policy.pdf",
            "TRAVEL", "travel_claims_policy.pdf"
    );

    private static final Pattern SECTION_SPLIT = Pattern.compile("(?=Section \\d+:)");

    private final PolicyClauseVectorRepository repository;
    private final EmbeddingModel embeddingModel;

    @Override
    public void run(ApplicationArguments args) {
        POLICY_PDFS_BY_PRODUCT_TYPE.forEach(this::seedProductType);
    }

    private void seedProductType(String productType, String resourceName) {
        if (!repository.findByProductType(productType).isEmpty()) {
            return;
        }

        String text = extractPdfText(resourceName);
        if (text == null || text.isBlank()) {
            log.warn("No text extracted from {}, skipping policy clause seeding for {}", resourceName, productType);
            return;
        }

        List<PolicyClauseVector> clauses = new ArrayList<>();
        for (String rawSection : SECTION_SPLIT.split(text)) {
            String section = rawSection.strip();
            if (section.isEmpty() || !section.startsWith("Section")) {
                continue;
            }
            String sectionTitle = section.lines().findFirst().orElse(section);
            float[] embedding = embeddingModel.embed(section);
            clauses.add(PolicyClauseVector.builder()
                    .productType(productType)
                    .section(sectionTitle)
                    .clauseText(section)
                    .embedding(toDoubleList(embedding))
                    .build());
        }

        if (clauses.isEmpty()) {
            log.warn("No 'Section N:' blocks found in {}, nothing seeded for {}", resourceName, productType);
            return;
        }

        repository.saveAll(clauses);
        log.info("Seeded {} policy clause(s) for productType={} from {}", clauses.size(), productType, resourceName);
    }

    private String extractPdfText(String resourceName) {
        try (var inputStream = new ClassPathResource(resourceName).getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            return new PDFTextStripper().getText(document);
        } catch (Exception e) {
            log.warn("Failed to extract text from {}: {}", resourceName, e.getMessage());
            return null;
        }
    }

    private static List<Double> toDoubleList(float[] embedding) {
        List<Double> result = new ArrayList<>(embedding.length);
        for (float f : embedding) {
            result.add((double) f);
        }
        return result;
    }
}
