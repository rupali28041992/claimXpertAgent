package com.nextgen.claims.dto;

import com.nextgen.claims.model.ClaimAnswer;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * The JSON part of the single multipart POST /api/claims/submit call.
 * Everything the user entered across Screens 1-4, held client-side until
 * now and sent together with the uploaded files.
 */
@Data
public class ClaimSubmitRequest {
    private String customerId;
    private String policyId;
    private String claimType;
    private String claimReason;
    private String freeText;
    private List<ClaimAnswer> answers;
    /** Maps each uploaded filename to its document category (from the upload UI). */
    private Map<String, String> fileDocumentTypes;
}
