package com.nextgen.claims.dto;

import lombok.Data;

/** Screen 1's optional "Suggest Claim Type" button - not RAG, plain zero-shot call. */
@Data
public class IntentSuggestRequest {
    private String freeText;
}
