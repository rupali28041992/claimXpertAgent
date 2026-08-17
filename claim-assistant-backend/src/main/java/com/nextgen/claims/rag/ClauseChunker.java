package com.nextgen.claims.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits extracted policy text into clause-sized chunks for embedding. Fixed max-chars with
 * overlap is the primary strategy - PDF text extraction (PDFBox under the hood via Tika) often
 * emits one newline per visual line with no blank-line paragraph breaks, so a paragraph-based
 * split alone would collapse whole pages into a single blob.
 */
@Component
public class ClauseChunker {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s*(\\d+(\\.\\d+)*\\.?\\s+.{0,80})$", Pattern.MULTILINE);

    @Value("${claims.rag.chunk-max-chars:1500}")
    private int maxChars;

    @Value("${claims.rag.chunk-overlap-chars:200}")
    private int overlapChars;

    public record Chunk(String text, String heading) {
    }

    public List<Chunk> chunk(String text) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String normalized = text.replace("\r\n", "\n");
        String currentHeading = null;

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + maxChars, normalized.length());
            String piece = normalized.substring(start, end);

            String headingForThisChunk = currentHeading; // heading in effect as this piece begins
            String headingInPiece = latestHeading(piece);
            if (headingInPiece != null) {
                currentHeading = headingInPiece; // carries forward to the next piece
            }

            String pieceText = headingForThisChunk != null ? headingForThisChunk + "\n" + piece.trim() : piece.trim();
            if (!pieceText.isBlank()) {
                chunks.add(new Chunk(pieceText, headingForThisChunk));
            }

            if (end == normalized.length()) {
                break;
            }
            start = end - overlapChars;
        }

        return chunks;
    }

    private String latestHeading(String piece) {
        Matcher matcher = HEADING_PATTERN.matcher(piece);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1).trim();
        }
        return last;
    }
}
