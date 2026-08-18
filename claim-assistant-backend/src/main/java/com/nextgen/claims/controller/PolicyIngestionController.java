package com.nextgen.claims.controller;

import com.nextgen.claims.service.PolicyIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyIngestionController {


    private final PolicyIngestionService policyIngestionService;

    @PostMapping(
            value = "/ingest",
            consumes = "multipart/form-data"
    )
    public ResponseEntity<Map<String, Object>> ingestPolicy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(
                    value = "productType",
                    defaultValue = "MEDICAL"
            )
            String productType) {

        int chunks =
                policyIngestionService.ingestPolicy(
                        file,
                        productType
                );

        return ResponseEntity.ok(
                Map.of(
                        "status", "INGESTED",
                        "productType", productType,
                        "chunksCreated", chunks
                )
        );
    }
}