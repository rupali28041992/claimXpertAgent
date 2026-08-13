package com.nextgen.claims.service;

import com.nextgen.claims.dto.PolicyLookupResponse;
import com.nextgen.claims.model.Policy;
import com.nextgen.claims.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;

    public PolicyLookupResponse lookup(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyNumber));

        return PolicyLookupResponse.builder()
                .policyId(policy.getPolicyNumber())
                .customerId(policy.getCustomerId())
                .claimType(policy.getClaimType())
                .policyholderName(policy.getPolicyholderName())
                .build();
    }
}
