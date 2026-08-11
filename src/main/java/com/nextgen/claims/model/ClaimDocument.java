package com.nextgen.claims.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * One uploaded document, produced by OCR + Validation Agent during claim
 * submission. Nested inside Claim.documents. No AI runs before this exists;
 * flags/clauseSatisfied are the ONLY fields the Validation Agent writes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimDocument {
    private String docType;
    private String fileRef;
    private String ocrText;
    private Map<String, String> extractedFields;
    private List<String> flags;
    private Boolean clauseSatisfied;
}
