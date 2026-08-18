package com.nextgen.claims.docvalidation.ingestion;

import com.nextgen.claims.docvalidation.model.PolicyClause;
import com.nextgen.claims.docvalidation.repository.PolicyClauseRepository;
import com.nextgen.claims.docvalidation.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time ingestion of a policy PDF into the "policy_clauses" collection
 * (Section 21/31 of the spec's RAG store). Splits the extracted text on
 * "SECTION X.Y" headers - the house style used by ClaimXpert's policy
 * documents - into one PolicyClause per section, embeds each via
 * EmbeddingService, and persists it. Triggered manually via
 * PolicyIngestionController; not part of the live claim-submission path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyClauseIngestor {

    private static final Pattern SECTION_HEADER = Pattern.compile("(?m)^\\s*SECTION\\s+\\d+\\.\\d+\\s*$");

    /**
     * Known section count for Medical_Insurance_Policy_ClaimXpert.pdf - a
     * mismatch here signals the PDF layout or PDFBox's extraction changed
     * and the split needs re-checking; ingestion still proceeds either way.
     */
    private static final int EXPECTED_MEDICAL_SECTION_COUNT = 22;

    private final EmbeddingService embeddingService;
    private final PolicyClauseRepository policyClauseRepository;

    public List<PolicyClause> ingest(File pdfFile, String claimType, String sourceDocument) {
        String text = extractText(pdfFile);
        List<Chunk> chunks = splitIntoSections(text);

        if ("MEDICAL".equalsIgnoreCase(claimType) && chunks.size() != EXPECTED_MEDICAL_SECTION_COUNT) {
            log.warn("[PolicyClauseIngestor] expected {} sections for {} but parsed {} - PDF layout or extraction may have changed",
                    EXPECTED_MEDICAL_SECTION_COUNT, sourceDocument, chunks.size());
        }

        policyClauseRepository.deleteByClaimTypeAndSourceDocument(claimType, sourceDocument);

        List<PolicyClause> saved = new ArrayList<>();
        for (Chunk chunk : chunks) {
            float[] embeddingVector = embeddingService.generateEmbedding(chunk.clauseText());
            PolicyClause clause = PolicyClause.builder()
                    .claimType(claimType)
                    .claimReason(chunk.title())
                    .clauseText(chunk.clauseText())
                    .embedding(toDoubleList(embeddingVector))
                    .sourceDocument(sourceDocument)
                    .build();
            saved.add(policyClauseRepository.save(clause));
            log.info("[PolicyClauseIngestor] ingested section='{}' claimType={} chars={}",
                    chunk.title(), claimType, chunk.clauseText().length());
        }

        log.info("[PolicyClauseIngestor] COMPLETE sourceDocument={} claimType={} clausesIngested={}",
                sourceDocument, claimType, saved.size());
        return saved;
    }

    private String extractText(File pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read policy PDF: " + pdfFile.getAbsolutePath(), e);
        }
    }

    private List<Chunk> splitIntoSections(String text) {
        List<int[]> headerSpans = new ArrayList<>();
        Matcher matcher = SECTION_HEADER.matcher(text);
        while (matcher.find()) {
            headerSpans.add(new int[]{matcher.start(), matcher.end()});
        }

        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < headerSpans.size(); i++) {
            int bodyStart = headerSpans.get(i)[1];
            int bodyEnd = i + 1 < headerSpans.size() ? headerSpans.get(i + 1)[0] : text.length();
            String body = text.substring(bodyStart, bodyEnd).trim();
            if (body.isEmpty()) {
                continue;
            }
            String[] lines = body.split("\\r?\\n", 2);
            String title = lines[0].trim();
            String rest = lines.length > 1 ? lines[1].trim() : "";
            chunks.add(new Chunk(title, title + "\n" + rest));
        }
        return chunks;
    }

    private List<Double> toDoubleList(float[] values) {
        List<Double> list = new ArrayList<>(values.length);
        for (float v : values) {
            list.add((double) v);
        }
        return list;
    }

    private record Chunk(String title, String clauseText) {
    }
}
