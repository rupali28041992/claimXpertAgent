package com.nextgen.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentSuggestResponse {
    private String claimType;
    private String claimReason;
    private double confidence;
}
