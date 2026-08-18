package com.nextgen.claims.controller;

import com.nextgen.claims.rag.PdfIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class PdfIngestionController {

    private final PdfIngestionService pdfIngestionService;

    /**
     * Upload a policy PDF to embed its clauses into MongoDB via bge-m3.
     *
     * POST /api/admin/ingest-pdf?productType=MEDICAL&force=false
     *
     * Multipart field: file (the PDF)
     *
     * Response: { "productType": "MEDICAL", "clausesIngested": 22 }
     *
     * force=true  → drops existing clauses for this productType and re-embeds
     * force=false → skips ingestion if clauses already exist (default, idempotent)
     */
    @PostMapping(value = "/ingest-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> ingestPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "productType", defaultValue = "MEDICAL") String productType,
            @RequestParam(value = "force", defaultValue = "false") boolean force) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No file received"));
        }

        String filename = file.getOriginalFilename();
        log.info("PDF ingestion request: file={}, productType={}, force={}", filename, productType, force);

        try {
            int count = pdfIngestionService.ingest(file.getInputStream(), productType, force);
            return ResponseEntity.ok(Map.of(
                    "productType", productType,
                    "filename", filename != null ? filename : "",
                    "clausesIngested", count,
                    "status", count > 0 ? "ingested" : "already_exists"
            ));
        } catch (Exception e) {
            log.error("PDF ingestion failed for {}", filename, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
