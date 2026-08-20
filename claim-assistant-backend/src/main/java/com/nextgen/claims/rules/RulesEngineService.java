package com.nextgen.claims.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gorules.zen_engine.ZenDecision;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Static claim-type config lookup, backed by a real GoRules JDM decision
 * graph (src/main/resources/rules/claim-type-config.json - editable in the
 * GoRules visual editor, https://editor.gorules.io, without touching any
 * Java code). No approve/reject or readiness decision lives here - that's
 * ClaimDecisionAgent's job now; this only returns which fields/documents a
 * claim type needs.
 */
@Service
@RequiredArgsConstructor
public class RulesEngineService {

    private final ZenDecisionRunner zenDecisionRunner;
    private final ObjectMapper objectMapper;

    private ZenDecision claimTypeConfigDecision;

    @PostConstruct
    void loadDecisionGraphs() {
        claimTypeConfigDecision = zenDecisionRunner.load(new ClassPathResource("rules/claim-type-config.json"));
    }

    /** Questions + required documents for a claim type - GoRules decision graph, no AI. */
    public ClaimTypeConfig getClaimTypeConfig(String claimType) {
        Map<String, Object> result = zenDecisionRunner.evaluate(claimTypeConfigDecision, Map.of("claimType", claimType));
        return objectMapper.convertValue(result, ClaimTypeConfig.class);
    }
}
