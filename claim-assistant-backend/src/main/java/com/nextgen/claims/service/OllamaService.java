package com.nextgen.claims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Isolates every Ollama chat call the POST /api/claims pipeline makes
 * (DocumentRelevanceAgent's borderline case) behind one place that never lets
 * an Ollama failure crash the request - it always throws a typed
 * OllamaException with one of OLLAMA_UNAVAILABLE / OLLAMA_TIMEOUT /
 * OLLAMA_INVALID_RESPONSE, which callers turn into a graceful fallback.
 *
 * Reuses the existing Spring AI ChatClient bean (AiConfig) - same
 * spring.ai.ollama.* configuration every other agent already uses, no
 * separate REST client or duplicated Ollama config.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private final ChatClient chatClient;

    public String generate(String prompt) {
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    public <T> T generateStructured(String prompt, Class<T> responseType) {
        try {
            return chatClient.prompt().user(prompt).call().entity(responseType);
        } catch (RuntimeException e) {
            throw classify(e);
        }
    }

    private OllamaException classify(RuntimeException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        Throwable cause = e.getCause();

        if (cause instanceof java.net.ConnectException || message.contains("connect")) {
            return new OllamaException("OLLAMA_UNAVAILABLE", e);
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return new OllamaException("OLLAMA_TIMEOUT", e);
        }
        if (cause instanceof JsonProcessingException || message.contains("json")) {
            return new OllamaException("OLLAMA_INVALID_RESPONSE", e);
        }
        return new OllamaException("OLLAMA_UNAVAILABLE", e);
    }

    public static class OllamaException extends RuntimeException {
        private final String errorCode;

        public OllamaException(String errorCode, Throwable cause) {
            super(errorCode, cause);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }
}
