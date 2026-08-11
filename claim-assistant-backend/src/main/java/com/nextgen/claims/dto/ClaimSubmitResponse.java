package com.nextgen.claims.dto;

import com.nextgen.claims.model.ClaimStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** What Screen 5 renders as the submit result. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimSubmitResponse {
    private String claimId;
    private Integer readinessScore;
    private List<String> flags;
    private String summary;
    private ClaimStatus status;
    private List<String> fileErrors; // set only when Step 2b rejected a file; claim was NOT saved
}
