package com.nextgen.claims.docvalidation.model;

import lombok.Data;

import java.util.Map;

/**
 * The JSON part of POST /api/docvalidation/claims (multipart, alongside
 * "files"). Distinct from com.nextgen.claims.dto.ClaimSubmitRequest, which
 * belongs to the existing /api/claims/submit flow and is untouched by this
 * module.
 */
@Data
public class ClaimRequest {
    private String claimType;
    private String claimReason;
    private Map<String, Object> answers;
}
