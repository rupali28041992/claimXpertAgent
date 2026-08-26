package com.nextgen.claims.seeder;

import com.nextgen.claims.model.Policy;
import com.nextgen.claims.model.User;
import com.nextgen.claims.repository.PolicyRepository;
import com.nextgen.claims.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Seeds two demo accounts on startup so a fresh MongoDB has working login credentials.
 * Idempotent — skipped if the user already exists.
 *
 * Account 1 — john.smith / john123
 *   Has an active MEDICAL policy. Can file and review claims.
 *
 * Account 2 — jane.doe / jane123
 *   Has no active policy. Sees "No Active Policies" when trying to file.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class UserPolicySeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedUser(
            User.builder()
                .userId("john.smith")
                .password("john123")
                .name("John Smith")
                .email("john.smith@demo.com")
                .customerId("CUST-001")
                .build()
        );

        seedUser(
            User.builder()
                .userId("jane.doe")
                .password("jane123")
                .name("Jane Doe")
                .email("jane.doe@demo.com")
                .customerId("CUST-002")
                .build()
        );

        // Active MEDICAL policy for john.smith (CUST-001)
        seedPolicy(
            Policy.builder()
                .policyNumber("POL-MED-2024-001")
                .customerId("CUST-001")
                .claimType("MEDICAL")
                .policyholderName("John Smith")
                .active(true)
                .startDate(Instant.now().minus(365, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(365, ChronoUnit.DAYS))
                .sumInsured(500000.0)
                .build()
        );

        // jane.doe (CUST-002) intentionally has no policies — no seed needed
    }

    private void seedUser(User user) {
        if (userRepository.existsById(user.getUserId())) {
            log.debug("[UserPolicySeeder] user {} already exists, skipping", user.getUserId());
            return;
        }
        userRepository.save(user);
        log.info("[UserPolicySeeder] seeded user {}", user.getUserId());
    }

    private void seedPolicy(Policy policy) {
        if (policyRepository.existsById(policy.getPolicyNumber())) {
            log.debug("[UserPolicySeeder] policy {} already exists, skipping", policy.getPolicyNumber());
            return;
        }
        policyRepository.save(policy);
        log.info("[UserPolicySeeder] seeded policy {} for customer {}", policy.getPolicyNumber(), policy.getCustomerId());
    }
}
