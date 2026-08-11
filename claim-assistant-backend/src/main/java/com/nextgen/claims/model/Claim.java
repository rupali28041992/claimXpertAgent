package com.nextgen.claims.model;

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

    private Instant createdAt;
}
