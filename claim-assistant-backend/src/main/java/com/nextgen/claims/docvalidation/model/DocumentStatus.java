package com.nextgen.claims.docvalidation.model;

public enum DocumentStatus {
    RECEIVED,
    VALIDATING_FILE,
    OCR_PROCESSING,
    OCR_COMPLETED,
    CHECKING_RELEVANCE,
    RELEVANT,
    IRRELEVANT,
    VALIDATING_POLICY,
    COMPLETED,
    FAILED
}
