package com.nextgen.claims.docvalidation.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * OCR/text-extraction contract for this pipeline (Section 12 of the spec).
 * Distinct from the existing com.nextgen.claims.service.OcrExtractionService,
 * which belongs to the live /api/claims/submit flow and is untouched here.
 */
public interface OcrService {

    /** Returns null if extraction fails (caller records OCR_FAILED and stops that document). */
    String extractText(MultipartFile file);
}
