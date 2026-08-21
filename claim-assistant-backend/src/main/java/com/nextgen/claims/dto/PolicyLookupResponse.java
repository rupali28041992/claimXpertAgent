package com.nextgen.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Screen 1's policy-number lookup result - resolves customerId/claimType so the user never picks claimType manually. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyLookupResponse {
    private String policyId;
    private String customerId;
    private String claimType;
    private String policyholderName;
}
