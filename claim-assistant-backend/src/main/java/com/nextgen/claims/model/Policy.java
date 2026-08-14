package com.nextgen.claims.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Seeded once per policy at onboarding, never written during a claim.
 * Screen 1 resolves customerId/claimType from a user-typed policyNumber
 * against this collection so the frontend no longer needs a claimType
 * radio button.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "policies")
public class Policy {

    @Id
    private String policyNumber; // human-entered natural key, e.g. "POL-2024-00123"

    @Indexed
    private String customerId;

    private String claimType; // TRAVEL / MEDICAL / MOTOR / LIFE

    private String policyholderName;
    private boolean active;
    private Instant startDate;
    private Instant endDate;
    private Double sumInsured;
}
