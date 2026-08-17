package com.nextgen.claims.agent;

import com.nextgen.claims.dto.IntentSuggestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Screen 1's optional "Suggest Claim Type" button. No RAG - only 4 claim
 * types, a plain zero-shot prompt is enough. Never called if the user
 * picks the claim type dropdown directly.
 */
@Component
@RequiredArgsConstructor
public class IntentClassificationAgent {

    private final ChatClient chatClient;

    private static final String CLAIM_TYPES = "TRAVEL, MEDICAL, MOTOR, LIFE";

    public IntentSuggestResponse suggest(String freeText) {
        String prompt = """
                A customer described their insurance issue in free text. Classify it into
                exactly one of these claim types: %s. Also give a short, specific claim
                reason (e.g. "Natural Death", "Flight Delay", "Theft", "Emergency Hospitalization")
                and a confidence score between 0 and 1.

                Customer text: "%s"
                """.formatted(CLAIM_TYPES, PromptTextSanitizer.sanitize(freeText));

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(IntentSuggestResponse.class);
    }
}
