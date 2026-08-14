package com.nextgen.claims.controller;

import com.nextgen.claims.dto.InvestigationResponse;
import com.nextgen.claims.service.ClaimOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class InvestigationController {

    private final ClaimOrchestrationService orchestrationService;

    @PostMapping("/investigate/{claimId}")
    public InvestigationResponse investigate(@PathVariable String claimId) {
        return orchestrationService.investigate(claimId);
    }

    @GetMapping("/investigate/{claimId}")
    public InvestigationResponse getInvestigation(@PathVariable String claimId) {
        return orchestrationService.getInvestigation(claimId);
    }
}
