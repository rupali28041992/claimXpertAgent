package com.nextgen.claims.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgen.claims.dto.QuestionnaireState;
import io.gorules.zen_engine.ZenDecision;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
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
    private ZenDecision questionEngineDecision;

    /** Loaded once at startup from question-definitions.json - pure JSON data, no logic. */
    private Map<String, Object> questionDefs;

    @PostConstruct
    void loadDecisionGraphs() throws IOException {
        claimTypeConfigDecision = zenDecisionRunner.load(new ClassPathResource("rules/claim-type-config.json"));
        questionEngineDecision = zenDecisionRunner.load(new ClassPathResource("rules/question-engine.json"));

        questionDefs = objectMapper.readValue(
                new ClassPathResource("rules/question-definitions.json").getInputStream(),
                new TypeReference<Map<String, Object>>() {});
    }

    /** Questions + required documents for a claim type - GoRules decision graph, no AI. */
    public ClaimTypeConfig getClaimTypeConfig(String claimType) {
        Map<String, Object> result = zenDecisionRunner.evaluate(claimTypeConfigDecision, Map.of("claimType", claimType));
        return objectMapper.convertValue(result, ClaimTypeConfig.class);
    }

    /**
     * Evaluates which questions to show next and derives claimType / claimReason.
     * Question definitions come from question-definitions.json (no code required to change them).
     * Routing/assembly logic lives in question-engine.json (editable in the GoRules visual editor).
     *
     * requiredDocuments is resolved directly from questionDefs in Java rather than relying on
     * GoRules to return nested arrays - this avoids serialisation quirks in the ZEN engine.
     */
    @SuppressWarnings("unchecked")
    public QuestionnaireState evaluateQuestions(Map<String, String> answers) {
        Map<String, Object> input = Map.of(
                "answers", answers != null ? answers : Map.of(),
                "questionDefs", questionDefs);
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
}
