package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Per-file outcome, nested inside ClaimContext/ClaimResult. Only file
 * validation and OCR happen per document now - relevance and per-document
 * clause validation were folded into ClaimDecisionAgent's single call, so
 * there are no relevance/validation fields here anymore.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResult {
    private String fileName;
    private String documentId;
    private boolean valid;
    private List<String> errors;

    private String ocrText;

    private DocumentStatus status;
}
