package com.nextgen.claims.controller;

import com.nextgen.claims.dto.PolicyVerifyResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policies")
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class PolicyController {

    @GetMapping("/{policyId}/verify")
    public PolicyVerifyResponse verifyPolicy(@PathVariable String policyId) {
        // Accepts formats like POL-1234, INSCO-AB9901, HC-200456 (2-6 letters, dash, 4+ alphanumeric)
        boolean valid = policyId != null
                && policyId.matches("(?i)^[A-Z]{2,6}-[A-Z0-9]{4,}$");
        if (valid) {
            return PolicyVerifyResponse.builder()
                    .valid(true)
                    .policyId(policyId.toUpperCase())
                    .holderName("Valued Policyholder")
                    .status("ACTIVE")
                    .build();
        }
        return PolicyVerifyResponse.builder()
                .valid(false)
                .policyId(policyId)
                .build();
    }
}
