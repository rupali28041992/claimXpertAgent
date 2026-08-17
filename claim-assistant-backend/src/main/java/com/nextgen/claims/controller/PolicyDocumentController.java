package com.nextgen.claims.controller;

import com.nextgen.claims.rag.PolicyDocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Admin path for ingesting policy documents beyond the two seeded at startup (see
 * PolicyDocumentSeeder) - new claim types (MOTOR/LIFE) or an updated version of an existing
 * one. No auth exists yet in this codebase, so this is unauthenticated like every other endpoint.
 */
@RestController
@RequestMapping("/api/admin/policy-documents")
@RequiredArgsConstructor
public class PolicyDocumentController {

    private final PolicyDocumentIngestionService ingestionService;

    public record IngestResponse(String claimType, int chunkCount) {
    }

    @PostMapping(consumes = "multipart/form-data")
    public IngestResponse upload(@RequestParam String claimType,
                                  @RequestParam(required = false) String riderCode,
                                  @RequestParam MultipartFile file) throws IOException {
        int chunkCount = ingestionService.ingest(claimType, riderCode, file.getInputStream(), file.getOriginalFilename());
        return new IngestResponse(claimType, chunkCount);
    }
}
