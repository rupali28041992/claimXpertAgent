package com.nextgen.claims.service;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts text from text-native PDF/DOCX files via Apache Tika. Shared by policy-document
 * ingestion and claim-document extraction ({@code OcrExtractionService}) - no OCR here, so
 * scanned/photographed documents with no text layer yield empty text.
 */
@Component
public class DocumentTextExtractor {

    public String extractText(InputStream content, String filename) {
        try {
            BodyContentHandler handler = new BodyContentHandler(-1); // no size limit
            new AutoDetectParser().parse(content, handler, new Metadata(), new ParseContext());
            return handler.toString();
        } catch (IOException | SAXException | TikaException e) {
            throw new DocumentExtractionException("Failed to extract text from " + filename, e);
        }
    }

    public static class DocumentExtractionException extends RuntimeException {
        public DocumentExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
