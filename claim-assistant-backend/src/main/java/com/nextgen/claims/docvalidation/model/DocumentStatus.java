package com.nextgen.claims.docvalidation.model;

/** DocumentAgent only ever produces one of these two outcomes now - file validation and OCR are the only per-document stages. */
public enum DocumentStatus {
    COMPLETED,
    FAILED
}
