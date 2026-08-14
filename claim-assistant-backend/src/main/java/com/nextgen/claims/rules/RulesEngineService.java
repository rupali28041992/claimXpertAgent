package com.nextgen.claims.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgen.claims.dto.ClaimClassifyRequest;
import com.nextgen.claims.dto.ClaimClassifyResponse;
import com.nextgen.claims.dto.DictionaryEntry;
import com.nextgen.claims.dto.QuestionnaireState;
import com.nextgen.claims.model.Claim;
import com.nextgen.claims.model.ClaimStatus;
import io.gorules.zen_engine.ZenDecision;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private ZenDecision questionEngineDecision;
    private ZenDecision questionnaireDecision;

    /** Loaded once at startup from question-definitions.json — pure JSON data, no logic. */
    private Map<String, Object> questionDefs;

    /**
     * Parsed from claims/dictionaries.json (GoRules policy document).
     * Keys: dictionary name (policyType, incidentType, claimType, claimReason, docType).
     * Values: ordered list of {label, value} entries — used by the frontend for dropdowns.
     */
    private Map<String, List<DictionaryEntry>> dictionaries = new LinkedHashMap<>();

    @PostConstruct
    @SuppressWarnings("unchecked")
    void loadDecisionGraphs() throws IOException {
        claimTypeConfigDecision = zenDecisionRunner.load(new ClassPathResource("rules/claim-type-config.json"));
        routingDecision         = zenDecisionRunner.load(new ClassPathResource("rules/claim-routing.json"));
        questionEngineDecision  = zenDecisionRunner.load(new ClassPathResource("rules/question-engine.json"));
        questionnaireDecision   = zenDecisionRunner.load(new ClassPathResource("rules/questionnaire.json"));

        questionDefs = objectMapper.readValue(
                new ClassPathResource("rules/question-definitions.json").getInputStream(),
                new TypeReference<Map<String, Object>>() {}
        );

        // Parse GoRules dictionaries (claims/dictionaries.json) into label/value entry lists.
        // These drive the frontend dropdowns and are the single source of truth for valid values.
        Map<String, Object> dictsDoc = objectMapper.readValue(
                new ClassPathResource("rules/claims/dictionaries.json").getInputStream(),
                new TypeReference<Map<String, Object>>() {}
        );
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) dictsDoc.get("blocks");
        for (Map<String, Object> block : blocks) {
            if (!"dictionary".equals(block.get("type"))) continue;
            Map<String, Object> data = (Map<String, Object>)
                    ((Map<String, Object>) block.get("props")).get("data");
            String name = (String) data.get("name");
            List<Map<String, Object>> entries = (List<Map<String, Object>>) data.get("entries");
            dictionaries.put(name, entries.stream()
                    .map(e -> new DictionaryEntry((String) e.get("label"), (String) e.get("value")))
                    .collect(Collectors.toList()));
        }
    }

    /** Returns all GoRules dictionary entries — used by the frontend to populate dropdowns. */
    public Map<String, List<DictionaryEntry>> getDictionaries() {
        return Collections.unmodifiableMap(dictionaries);
    }

    /** Returns the entries for a single named dictionary (e.g. "policyType"). */
    public List<DictionaryEntry> getDictionary(String name) {
        return dictionaries.getOrDefault(name, Collections.emptyList());
    }

    /** Questions + required documents for a claim type — data comes from question-definitions.json. */
    public ClaimTypeConfig getClaimTypeConfig(String claimType) {
        Map<String, Object> result = zenDecisionRunner.evaluate(
                claimTypeConfigDecision,
                Map.of("claimType", claimType, "questionDefs", questionDefs));
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

    /**
     * Evaluates which questions to show next and derives claimType / claimReason.
     * Question definitions come from question-definitions.json (no code required to change them).
     * Routing/assembly logic lives in question-engine.json (editable in the GoRules visual editor).
     *
     * requiredDocuments is resolved directly from questionDefs in Java rather than relying on
     * GoRules to return nested arrays — this avoids serialisation quirks in the ZEN engine.
     */
    @SuppressWarnings("unchecked")
    public QuestionnaireState evaluateQuestions(Map<String, String> answers) {
        Map<String, Object> input = Map.of(
                "answers",      answers != null ? answers : Map.of(),
                "questionDefs", questionDefs
        );
        Map<String, Object> result = zenDecisionRunner.evaluate(questionEngineDecision, input);
        QuestionnaireState state = objectMapper.convertValue(result, QuestionnaireState.class);

        // Populate requiredDocuments reliably from questionDefs (Java side).
        // GoRules may not serialise complex nested arrays correctly in all versions.
        if (state.isComplete() && state.getClaimType() != null) {
            Object categoryConfig = questionDefs.get(state.getClaimType());
            if (categoryConfig instanceof Map<?, ?> configMap) {
                Object rawDocs = configMap.get("requiredDocuments");
                if (rawDocs instanceof List<?> rawList && !rawList.isEmpty()) {
                    List<QuestionnaireState.DocumentCategory> docs = objectMapper.convertValue(
                            rawList,
                            objectMapper.getTypeFactory().constructCollectionType(
                                    List.class, QuestionnaireState.DocumentCategory.class));
                    state.setRequiredDocuments(docs);
                } else {
                    state.setRequiredDocuments(Collections.emptyList());
                }
            }
        }

        return state;
    }

    /**
     * Evaluates the GoRules questionnaire.json decision graph.
     * Graph flow:
     *   inputNode → switchNode (policyType) → decisionTableNode (claimType + claimReason)
     *             → decisionTableNode (baseDocuments) → expressionNode (requiredDocuments enrichment)
     *
     * All routing logic and document lists live in questionnaire.json — no Java code changes
     * are needed when the business rules change; update the JSON in GoRules editor and redeploy.
     */
    public ClaimClassifyResponse classify(ClaimClassifyRequest request) {
        // Use LinkedHashMap so null values are serialised as JSON null (not omitted)
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("policyType",        request.getPolicyType());
        input.put("incidentType",      request.getIncidentType());
        input.put("injuryInvolved",    request.getInjuryInvolved());
        input.put("thirdPartyInvolved",request.getThirdPartyInvolved());
        input.put("policeReportFiled", request.getPoliceReportFiled());

        Map<String, Object> result = zenDecisionRunner.evaluate(questionnaireDecision, input);
        return objectMapper.convertValue(result, ClaimClassifyResponse.class);
    }

    public record RoutingDecision(ClaimStatus status, String reason) {
    }
}
