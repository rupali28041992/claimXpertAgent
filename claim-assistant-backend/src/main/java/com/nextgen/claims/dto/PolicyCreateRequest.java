package com.nextgen.claims.dto;

import lombok.Data;

import java.time.Instant;

/** New Policy Screen's "Save Policy" form. */
@Data
public class PolicyCreateRequest {
    private String policyNumber;
    private String customerId;
    private String claimType;
    private String policyholderName;
    private Double sumInsured;
    private Instant startDate;
    private Instant endDate;
}
