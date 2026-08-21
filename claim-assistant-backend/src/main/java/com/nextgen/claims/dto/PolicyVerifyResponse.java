package com.nextgen.claims.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PolicyVerifyResponse {
    private boolean valid;
    private String policyId;
    private String holderName;
    private String status;
}
