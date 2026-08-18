package com.nextgen.claims.service;

import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.repository.PolicyClauseVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyIngestionService {

    private final PolicyClauseVectorRepository repository;
    private final EmbeddingModel embeddingModel;

    private static final Pattern SECTION_PATTERN =
            Pattern.compile(
                    "(?m)^SECTION\\s+(\\d+\\.\\d+)\\s*\\n(.+?)(?=\\nSECTION\\s+\\d+\\.\\d+|\\z)",
                    Pattern.DOTALL
            );

    public int ingestPolicy(MultipartFile file, String productType) {

        try {
            String policyText = extractText(file);

            if (policyText == null || policyText.isBlank()) {
                throw new IllegalArgumentException(
                        "No text could be extracted from policy PDF"
                );
            }

            List<PolicyClauseVector> vectors =
                    createEmbeddings(policyText, productType);

            repository.deleteAll(
                    repository.findByProductType(productType)
            );

            repository.saveAll(vectors);

            log.info(
                    "Policy ingestion completed. productType={}, chunks={}",
                    productType,
                    vectors.size()
            );

            return vectors.size();

        } catch (Exception e) {

            log.error(
                    "Policy ingestion failed for productType={}",
                    productType,
                    e
            );

            throw new RuntimeException(
                    "Policy ingestion failed",
                    e
            );
        }
    }

    private String extractText(MultipartFile file) throws Exception {

        try (PDDocument document =
                     Loader.loadPDF(file.getBytes())) {

            PDFTextStripper stripper =
                    new PDFTextStripper();

            return stripper.getText(document);
        }
    }

    private List<PolicyClauseVector> createEmbeddings(
            String policyText,
            String productType) {

        List<PolicyClauseVector> vectors =
                new ArrayList<>();

        Matcher matcher =
                SECTION_PATTERN.matcher(policyText);

        while (matcher.find()) {

            String sectionNumber =
                    matcher.group(1).trim();

            String sectionContent =
                    matcher.group(2).trim();

            String sectionTitle =
                    extractSectionTitle(sectionContent);

            String clauseText =
                    sectionContent.trim();

            if (clauseText.isBlank()) {
                continue;
            }

            float[] embedding =
                    embeddingModel.embed(clauseText);

            List<Double> embeddingList =
                    toDoubleList(embedding);

            PolicyClauseVector vector =
                    PolicyClauseVector.builder()
                            .productType(productType)
                            .riderCode(null)
                            .section(
                                    "SECTION "
                                            + sectionNumber
                                            + " - "
                                            + sectionTitle
                            )
                            .clauseText(clauseText)
                            .embedding(embeddingList)
                            .build();

            vectors.add(vector);
        }

        return vectors;
    }

    private String extractSectionTitle(String sectionContent) {

        String[] lines =
                sectionContent.split("\\R");

        if (lines.length == 0) {
            return "Unknown";
        }

        return lines[0].trim();
    }

    private List<Double> toDoubleList(float[] embedding) {

        List<Double> result =
                new ArrayList<>(embedding.length);

        for (float value : embedding) {
            result.add((double) value);
        }

        return result;
    }
}