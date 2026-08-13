package com.nextgen.claims.controller;

import com.nextgen.claims.dto.PolicyCreateRequest;
import com.nextgen.claims.model.Policy;
import com.nextgen.claims.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** "Add Policy" screen - lets ops save a new policy record. No AI here. */
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    public Policy create(@RequestBody PolicyCreateRequest request) {
        return policyService.create(request);
    }
}
