package com.nextgen.claims.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * productType -> classpath PDF filename for PolicyClauseSeeder's startup seeding.
 * Bound from claims.policy-seed.pdfs in application.yml so a new product type
 * (e.g. MOTOR) is a config change, not a Java code change.
 *
 * enabled: set to false once ingestion is done and you no longer want
 * PolicyClauseSeeder doing anything at startup (not even the idempotency check).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "claims.policy-seed")
public class PolicySeedProperties {
    private boolean enabled = true;
    private Map<String, String> pdfs = Map.of();
}
