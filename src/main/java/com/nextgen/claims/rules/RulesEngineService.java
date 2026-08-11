package com.nextgen.claims.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.ClaimStatus;
import io.gorules.zen_engine.ZenDecision;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Business rules, backed by real GoRules JDM decision graphs
 * (src/main/resources/rules/*.json - editable in the GoRules visual editor,
 * https://editor.gorules.io, without touching any Java code). This class
 * only loads the graphs once and shuttles plain Java in/out through
 * ZenDecisionRunner; it never contains the rule logic itself.
 */
@Service
@RequiredArgsConstructor
public class RulesEngineService {

    private final ZenDecisionRunner zenDecisionRunner;
    private final ObjectMapper objectMapper;

    @Value("${claims.routing.auto-approve-max-amount:50000}")
    private long autoApproveMaxAmount;

    private ZenDecision claimTypeConfigDecision;
    private ZenDecision routingDecision;

    @PostConstruct
    void loadDecisionGraphs() {
        claimTypeConfigDecision = zenDecisionRunner.load(new ClassPathResource("rules/claim-type-config.json"));
        routingDecision = zenDecisionRunner.load(new ClassPathResource("rules/claim-routing.json"));
    }

    /** Questions + required documents for a claim type - GoRules decision graph, no AI. */
    public ClaimTypeConfig getClaimTypeConfig(String claimType) {
        Map<String, Object> result = zenDecisionRunner.evaluate(claimTypeConfigDecision, Map.of("claimType", claimType));
        return objectMapper.convertValue(result, ClaimTypeConfig.class);
    }

    /** Hard rules (no documents at all) - kept in plain Java since it's a single trivial check, not table-worthy yet. */
    public String evaluateHardRules(Claim claim) {
        if (claim.getDocuments() == null || claim.getDocuments().isEmpty()) {
            return "No documents submitted";
        }
        return null;
    }

    /** Routing decision (auto-approve / auto-reject / under-review) - GoRules decision graph, no AI. */
    public RoutingDecision route(Claim claim, String hardRuleFailureReason) {
        if (hardRuleFailureReason != null) {
            return new RoutingDecision(ClaimStatus.AUTO_REJECTED, hardRuleFailureReason);
        }

        int flagCount = claim.getFlagsAtSubmission() == null ? 0 : claim.getFlagsAtSubmission().size();
        long claimAmount = 0L; // wire to a real claimed-amount field once captured on the form

        Map<String, Object> input = Map.of(
                "documentCount", claim.getDocuments() == null ? 0 : claim.getDocuments().size(),
                "flagCount", flagCount,
                "claimAmount", claimAmount,
                "autoApproveMaxAmount", autoApproveMaxAmount
        );

        Map<String, Object> result = zenDecisionRunner.evaluate(routingDecision, input);
        ClaimStatus status = ClaimStatus.valueOf((String) result.get("status"));
        String reason = (String) result.get("reason");
        return new RoutingDecision(status, reason);
    }

    public record RoutingDecision(ClaimStatus status, String reason) {
    }
}
