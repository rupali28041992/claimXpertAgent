package com.nextgen.claims.rag;

import com.nextgen.claims.model.PolicyClauseVector;
import com.nextgen.claims.repository.PolicyClauseVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
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

    private static final Tika TIKA = new Tika();

    // Case-insensitive: matches "Section 4.1", "SECTION 4.1", "Section 10.2 – Title"
    // Group 1 = section number, Group 2 = optional inline title after dash
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^(?:Section|SECTION)\\s+(\\d+\\.\\d+)(?:\\s*[\\u2013\\u2014\\-]\\s*(.+))?$",
            Pattern.MULTILINE
    );

    public int ingest(InputStream pdfStream, String productType, boolean force) throws IOException, TikaException {
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
            repository.save(PolicyClauseVector.builder()
                    .id(UUID.randomUUID().toString())
                    .productType(productType)
                    .section(c.section())
                    .clauseText(c.body())
                    .embedding(toDoubleList(raw))
                    .build());
            saved++;
            log.debug("Embedded & stored: {}", c.section());
        }

        log.info("PDF ingestion complete — {} clauses stored for {}", saved, productType);
        return saved;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private String extractText(InputStream stream) throws IOException, TikaException {
        TIKA.setMaxStringLength(-1); // no truncation
        return TIKA.parseToString(stream);
    }

    /**
     * Deduplicates TOC entries vs actual clause text by keeping the LAST occurrence
     * of each section number — TOC references appear first, actual clause body last.
     */
    private List<Chunk> chunk(String text) {
        Matcher m = SECTION_PATTERN.matcher(text);
        record Hit(int start, int end, String num, String inlineTitle) {}
        List<Hit> hits = new ArrayList<>();
        while (m.find()) {
            hits.add(new Hit(m.start(), m.end(),
                    m.group(1),
                    m.group(2) != null ? m.group(2).strip() : ""));
        }

        if (hits.isEmpty()) {
            log.warn("No 'Section X.X' headers found in extracted text — check document format");
            return List.of();
        }

        // Keep only the last occurrence of each section number
        Map<String, Hit> lastByNum = new LinkedHashMap<>();
        for (Hit h : hits) lastByNum.put(h.num(), h);

        List<Hit> deduped = new ArrayList<>(lastByNum.values());
        deduped.sort(Comparator.comparingInt(Hit::start));

        List<Chunk> results = new ArrayList<>();
        for (int i = 0; i < deduped.size(); i++) {
            Hit h       = deduped.get(i);
            int bodyEnd = (i + 1 < deduped.size()) ? deduped.get(i + 1).start() : text.length();

            String section     = "Section " + h.num();
            String inlineTitle = h.inlineTitle();
            String rawBody     = text.substring(h.end(), bodyEnd);

            if (inlineTitle.isEmpty()) {
                String[] lines = rawBody.split("\\r?\\n", 3);
                if (lines.length > 0) {
                    String candidate = lines[0].strip();
                    if (!candidate.isEmpty() && candidate.length() < 70 && !candidate.contains(".")) {
                        inlineTitle = candidate;
                        rawBody = lines.length > 1
                                ? String.join("\n", Arrays.copyOfRange(lines, 1, lines.length))
                                : "";
                    }
                }
            }

            if (!inlineTitle.isEmpty()) section = section + " – " + inlineTitle;

            String body = cleanBody(rawBody);
            if (!body.isBlank()) results.add(new Chunk(section, body));
        }
        return results;
    }

    private String cleanBody(String raw) {
        return raw.lines()
                .map(String::strip)
                .filter(l -> l.length() > 10)
                .filter(l -> !l.matches("\\d+"))
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
