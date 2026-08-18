package com.nextgen.claims.model;

import com.nextgen.claims.dto.ClaimDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * The single collection the whole claim lives in. Screens 1-4 are held in
 * Angular state and never touch Mongo; this document is created once, in
 * full, at the moment the user clicks Submit on Screen 4. Later steps
 * (adjuster review) update this same document in place.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "claims")
public class Claim {

    @Id
    private String claimId;

    private String customerId;
    private String policyId;

    private String claimType;   // TRAVEL / MEDICAL / MOTOR / LIFE
    private String claimReason;
    private String freeText;    // optional Screen 1 description, if user typed one

    private List<ClaimAnswer> answers;
    private List<ClaimDocument> documents;

    private Integer readinessScore;
    private List<String> flagsAtSubmission;

    private ClaimStatus status;
    private List<StatusChange> statusHistory;

    // Pipeline status for the POST /api/claims multi-agent flow (ClaimOrchestrator):
    // RECEIVED / PROCESSING / COMPLETED / PARTIALLY_COMPLETED / FAILED. Independent of
    // `status` above, which is the adjuster-facing adjudication status for the wizard flow.
    private String processingStatus;

    // ClaimDecisionAgent's claim-level verdict for the POST /api/claims flow. `status`
    // above is set from finalDecision.verdict (AUTO_APPROVED/AUTO_REJECTED/UNDER_REVIEW)
    // so the existing adjuster ReviewController can act on it exactly like a wizard claim.
    private ClaimDecision finalDecision;

    private Instant createdAt;
}
