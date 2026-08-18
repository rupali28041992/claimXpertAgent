package com.nextgen.claims.controller;
import com.nextgen.claims.dto.PolicyVerifyResponse;
import org.springframework.web.bind.annotation.*;
import com.nextgen.claims.dto.PolicyCreateRequest;
import com.nextgen.claims.model.Policy;
import com.nextgen.claims.service.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping("/{policyId}/verify")
    public PolicyVerifyResponse verifyPolicy(@PathVariable String policyId) {
        try {
            var lookup = policyService.lookup(policyId);
            return PolicyVerifyResponse.builder()
                    .valid(true)
                    .policyId(lookup.getPolicyId())
                    .holderName(lookup.getPolicyholderName())
                    .status("ACTIVE")
                    .build();
        } catch (IllegalArgumentException e) {
            return PolicyVerifyResponse.builder()
                    .valid(false)
                    .policyId(policyId)
                    .build();
        }
    }

    @PostMapping
    public Policy create(@RequestBody PolicyCreateRequest request) {
        return policyService.create(request);
    }
}
