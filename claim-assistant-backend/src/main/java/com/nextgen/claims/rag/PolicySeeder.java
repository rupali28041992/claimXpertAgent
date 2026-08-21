package com.nextgen.claims.rag;

import com.nextgen.claims.model.Policy;
import com.nextgen.claims.repository.PolicyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Seeds demo policies into MongoDB once at startup (idempotent).
 * These are the policy numbers accepted by the claim portal's policy-gate
 * screen. Add or edit entries here to expose different test policies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicySeeder {

    private final PolicyRepository policyRepository;

    @PostConstruct
    void seed() {
        List<Policy> demo = List.of(
            Policy.builder()
                .policyNumber("POL-100234")
                .customerId("cust-001")
                .claimType("MEDICAL")
                .policyholderName("Rajesh Kumar Sharma")
                .active(true)
                .startDate(Instant.parse("2024-01-01T00:00:00Z"))
                .endDate(Instant.parse("2027-01-01T00:00:00Z"))
                .sumInsured(500000.0)
                .build(),

            Policy.builder()
                .policyNumber("POL-200345")
                .customerId("cust-002")
                .claimType("MOTOR")
                .policyholderName("Anita Desai")
                .active(true)
                .startDate(Instant.parse("2024-03-01T00:00:00Z"))
                .endDate(Instant.parse("2027-03-01T00:00:00Z"))
                .sumInsured(300000.0)
                .build(),

            Policy.builder()
                .policyNumber("POL-300456")
                .customerId("cust-003")
                .claimType("TRAVEL")
                .policyholderName("Vikram Nair")
                .active(true)
                .startDate(Instant.parse("2024-06-01T00:00:00Z"))
                .endDate(Instant.parse("2027-06-01T00:00:00Z"))
                .sumInsured(200000.0)
                .build(),

            Policy.builder()
                .policyNumber("POL-123456")
                .customerId("cust-004")
                .claimType("MEDICAL")
                .policyholderName("Priya Mehta")
                .active(true)
                .startDate(Instant.parse("2024-02-01T00:00:00Z"))
                .endDate(Instant.parse("2027-02-01T00:00:00Z"))
                .sumInsured(750000.0)
                .build()
        );

        int seeded = 0;
        for (Policy p : demo) {
            if (policyRepository.findById(p.getPolicyNumber()).isEmpty()) {
                policyRepository.save(p);
                seeded++;
            }
        }

        if (seeded > 0) {
            log.info("PolicySeeder: inserted {} demo policy record(s).", seeded);
        } else {
            log.info("PolicySeeder: all demo policies already present — skipping.");
        }
    }
}
