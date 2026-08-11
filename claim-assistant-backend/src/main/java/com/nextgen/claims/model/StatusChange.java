package com.nextgen.claims.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One entry in Claim.statusHistory. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusChange {
    private ClaimStatus status;
    private Instant at;
    private String reason;
    private String changedBy; // "SYSTEM" for automated transitions, else adjuster id
}
