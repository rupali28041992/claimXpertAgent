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
@CrossOrigin(origins = "${claims.cors.allowed-origin:http://localhost:4200}")
public class PolicyController {

    private final PolicyService policyService;

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
        return PolicyVerifyResponse.builder().valid(false).policyId(policyId).build();
    }

    @PostMapping
    public Policy create(@RequestBody PolicyCreateRequest request) {
        return policyService.create(request);
    }
}
