package com.nextgen.claims.docvalidation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persisted claim state for this pipeline (Section 31 of the spec), stored
 * in a NEW "docvalidation_claims" collection - deliberately not the
 * existing "claims" collection used by com.nextgen.claims.model.Claim /
 * the live /api/claims/submit flow, to avoid schema collision.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "docvalidation_claims")
public class ClaimEntity {

    @Id
    private String claimId;

    private String claimType;
    private String claimReason;
    private Map<String, Object> answers;

    private List<DocumentResult> documents;

    private ClaimProcessingStatus status;

    private Instant createdAt;
    private Instant updatedAt;
}
