package com.nextgen.claims.docvalidation.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component("ollama")
@RequiredArgsConstructor
public class OllamaHealthIndicator implements HealthIndicator {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model:qwen3.5:9b}")
    private String chatModel;

    @Override
    public Health health() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(ollamaBaseUrl + "/api/tags", String.class);
            if (response != null && response.contains(chatModel.split(":")[0])) {
                return Health.up()
                        .withDetail("model", chatModel)
                        .withDetail("url", ollamaBaseUrl)
                        .build();
            }
            return Health.unknown()
                    .withDetail("model", chatModel + " not found in /api/tags")
                    .withDetail("url", ollamaBaseUrl)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("url", ollamaBaseUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
