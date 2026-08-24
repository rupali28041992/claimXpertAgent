package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResult {

    private String fileName;

    private String documentId;

    private boolean valid;

    private List<String> errors;

    /**
     * Structured information extracted from OCR.
     *
     * IMPORTANT:
     * Raw OCR text must not be sent to ClaimDecisionAgent/Ollama.
     */
    private DocumentEvidence evidence;

    private DocumentStatus status;
}