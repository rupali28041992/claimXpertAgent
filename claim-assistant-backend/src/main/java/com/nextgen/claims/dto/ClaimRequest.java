package com.nextgen.claims.dto;

import lombok.Data;

import java.util.Map;

/** The JSON-decoded shape of POST /api/claims' claimType/claimReason/answers parts. */
@Data
public class ClaimRequest {
    private String claimType;
    private String claimReason;
    private Map<String, Object> answers;
}
