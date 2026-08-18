package com.nextgen.claims.rag;

import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.repository.PolicyClauseVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfIngestionService {

    private final PolicyClauseVectorRepository repository;
    private final EmbeddingModel embeddingModel;

    // Case-insensitive; matches BOTH real-world numbering conventions seen across policy
    // PDFs: colon-style "Section 1: Hospitalization Claims" (the bundled sample PDFs) and
    // dot-style "Section 4.1 – In-patient Hospitalisation" (used by some uploaded policies),
    // with or without a separator at all ("Section 5 Maternity Claims").
    // Group 1 = section number (e.g. "1" or "4.1"), Group 2 = inline title on the same line (may be empty)
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^(?:Section|SECTION)\\s+(\\d+(?:\\.\\d+)?)\\s*[:\\u2013\\u2014\\-]?\\s*(.*)$",
            Pattern.MULTILINE
    );

    /**
     * Extracts text from the PDF, splits into per-section chunks, embeds each chunk
     * with nomic-embed-text via Ollama, and saves to MongoDB policy_clause_vectors.
     *
     * @param pdfStream   raw bytes of the PDF
     * @param productType e.g. "MEDICAL", "MOTOR", "TRAVEL"
     * @param force       when true, drops existing clauses for productType before re-ingesting
     * @return number of clauses actually written
     */
    public int ingest(InputStream pdfStream, String productType, boolean force) throws IOException {
        if (force) {
            List<PolicyClauseVector> existing = repository.findByProductType(productType);
            if (!existing.isEmpty()) {
                repository.deleteAll(existing);
                log.info("Dropped {} existing {} clauses (force re-ingest)", existing.size(), productType);
            }
        } else {
            if (!repository.findByProductType(productType).isEmpty()) {
                log.info("{} clauses already present — skipping (use force=true to re-ingest)", productType);
                return 0;
            }
        }

        String fullText = extractText(pdfStream);
        List<Chunk> chunks = chunk(fullText);
        log.info("PDF parsed into {} section chunks for productType={}", chunks.size(), productType);

        int saved = 0;
        for (Chunk c : chunks) {
            float[] raw = embeddingModel.embed(c.section() + ". " + c.body());
            List<Double> embedding = toDoubleList(raw);

            repository.save(PolicyClauseVector.builder()
                    .id(UUID.randomUUID().toString())
                    .productType(productType)
                    .section(c.section())
                    .clauseText(c.body())
                    .embedding(embedding)
                    .build());
            saved++;
            log.debug("Embedded & stored: {}", c.section());
        }

        log.info("PDF ingestion complete — {} clauses stored for {}", saved, productType);
        return saved;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private String extractText(InputStream pdfStream) throws IOException {
        byte[] bytes = pdfStream.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    /**
     * Splits PDF text into section chunks. Strategy:
     * 1. Collect ALL matches of "Section/SECTION X.X" with their positions.
     * 2. Group by section number, keeping only the LAST position of each.
     *    This eliminates TOC entries (which appear first) in favour of the
     *    actual clause text (which appears later in the document).
     * 3. Build chunk bodies from the text between consecutive last-occurrences.
     */
    private List<Chunk> chunk(String text) {
        // Step 1: find all section header positions
        Matcher m = SECTION_PATTERN.matcher(text);
        record Hit(int start, int end, String num, String inlineTitle) {}
        List<Hit> hits = new ArrayList<>();
        while (m.find()) {
            hits.add(new Hit(m.start(), m.end(),
                    m.group(1),
                    m.group(2) != null ? m.group(2).strip() : ""));
        }

        if (hits.isEmpty()) {
            log.warn("No 'Section N' or 'Section N.M' headers found in extracted PDF text — check PDF format");
            return List.of();
        }

        // Step 2: for each section number keep only the last hit (content > TOC)
        Map<String, Hit> lastByNum = new LinkedHashMap<>();
        for (Hit h : hits) {
            lastByNum.put(h.num(), h); // later hit overwrites earlier
        }

        // Sort last-hits by position in document
        List<Hit> deduped = new ArrayList<>(lastByNum.values());
        deduped.sort(Comparator.comparingInt(Hit::start));

        log.debug("Found {} unique section numbers after deduplication", deduped.size());

        // Step 3: build chunks
        List<Chunk> results = new ArrayList<>();
        for (int i = 0; i < deduped.size(); i++) {
            Hit h = deduped.get(i);
            int bodyEnd = (i + 1 < deduped.size()) ? deduped.get(i + 1).start() : text.length();

            // Build section label: "Section 4.1" or "Section 4.1 – Title"
            String section = "Section " + h.num();
            String inlineTitle = h.inlineTitle();

            String rawBody = text.substring(h.end(), bodyEnd);

            // If no inline title, check whether the first short line is the title
            if (inlineTitle.isEmpty()) {
                String[] lines = rawBody.split("\\r?\\n", 3);
                if (lines.length > 0) {
                    String candidate = lines[0].strip();
                    // Accept as title: non-empty, short, no period (not a sentence)
                    if (!candidate.isEmpty() && candidate.length() < 70 && !candidate.contains(".")) {
                        inlineTitle = candidate;
                        rawBody = lines.length > 1
                                ? String.join("\n", Arrays.copyOfRange(lines, 1, lines.length))
                                : "";
                    }
                }
            }

            if (!inlineTitle.isEmpty()) {
                section = section + " – " + inlineTitle; // en-dash
            }

            String body = cleanBody(rawBody);
            if (!body.isBlank()) {
                results.add(new Chunk(section, body));
                log.debug("  chunk: {}", section);
            }
        }
        return results;
    }

    private String cleanBody(String raw) {
        return raw.lines()
                .map(String::strip)
                .filter(l -> l.length() > 10)       // drop short layout noise
                .filter(l -> !l.matches("\\d+"))    // drop bare page-number lines
                .collect(Collectors.joining(" "))
                .strip();
    }

    private List<Double> toDoubleList(float[] raw) {
        List<Double> list = new ArrayList<>(raw.length);
        for (float v : raw) list.add((double) v);
        return list;
    }

    private record Chunk(String section, String body) {}
}
