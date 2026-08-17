package com.nextgen.claims.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gorules.zen_engine.ZenDecision;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Questions/required-documents lookup, backed by a real GoRules JDM decision graph
 * (src/main/resources/rules/claim-type-config.json - editable in the GoRules visual editor,
 * https://editor.gorules.io, without touching any Java code). This class only loads the graph
 * once and shuttles plain Java in/out through ZenDecisionRunner; it never contains the rule
 * logic itself. The automated approve/reject/under-review decision used to live here too
 * (claim-routing.json) but has moved to ClaimDecisionAgent, an LLM decision informed by RAG'd
 * policy clauses, answers, and document content.
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
