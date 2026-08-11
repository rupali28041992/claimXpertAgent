package com.nextgen.claims.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gorules.zen_engine.JsonBuffer;
import io.gorules.zen_engine.ZenDecision;
import io.gorules.zen_engine.ZenEngine;
import io.gorules.zen_engine.ZenEvaluateOptions;
import io.gorules.zen_engine.ZenException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Thin wrapper around GoRules' ZenEngine (Maven: io.gorules:zen-engine).
 * Loads a JDM decision graph (.json, editable in the GoRules visual editor)
 * once at startup and evaluates it with plain Java maps in/out - no other
 * class in this codebase touches the ZenEngine API directly.
 */
@Component
@RequiredArgsConstructor
public class ZenDecisionRunner {

    private final ObjectMapper objectMapper;
    private final ZenEngine zenEngine = new ZenEngine(null,null);

    public ZenDecision load(Resource decisionGraph) {
        try {
            String content = new String(decisionGraph.getInputStream().readAllBytes());
            return zenEngine.createDecision(new JsonBuffer(content));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read decision graph: " + decisionGraph, e);
        } catch (ZenException e) {
            throw new IllegalStateException("Invalid decision graph: " + decisionGraph, e);
        }
    }

    public Map<String, Object> evaluate(ZenDecision decision, Map<String, Object> input) {
        try {
            String inputJson = objectMapper.writeValueAsString(input);
            var response = decision.evaluate(new JsonBuffer(inputJson), new ZenEvaluateOptions(null, null)).get();
            String resultJson = response.result().toString();
            return objectMapper.readValue(resultJson, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Decision evaluation failed", e);
        }
    }
}
