package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Final API response shape for POST /api/docvalidation/claims (Section 33 of the spec). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimResult {
    private String claimId;
    private ClaimProcessingStatus status;
    private List<DocumentResult> documents;
}
