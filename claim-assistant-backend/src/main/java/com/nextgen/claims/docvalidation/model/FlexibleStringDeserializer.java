package com.nextgen.claims.docvalidation.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * Accepts either a JSON string or a JSON object for a single list element.
 * When the model returns clause objects instead of strings, we extract
 * "clauseText" if present, otherwise fall back to the raw JSON.
 */
public class FlexibleStringDeserializer extends StdDeserializer<String> {

    public FlexibleStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            return p.getText();
        }
        // Model returned an object — extract clauseText or fall back to JSON
        ObjectNode node = p.getCodec().readTree(p);
        if (node.has("clauseText") && !node.get("clauseText").isNull()) {
            return node.get("clauseText").asText();
        }
        if (node.has("text") && !node.get("text").isNull()) {
            return node.get("text").asText();
        }
        if (node.has("clause") && !node.get("clause").isNull()) {
            return node.get("clause").asText();
        }
        return node.toString();
    }
}
