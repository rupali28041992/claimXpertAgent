package com.nextgen.claims.docvalidation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Single point of contact with Ollama for this module (Section 18 of the
 * spec) - reuses the existing ChatClient bean from
 * com.nextgen.claims.config.AiConfig rather than creating a second
 * RestClient/HTTP client. All Ollama failures are caught here and
 * translated into OllamaServiceException so nothing raw ever reaches the
 * frontend (Section 35/36).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private final ChatClient chatClient;

    public String generate(String prompt) {
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            throw translate(e);
        }
    }

    public <T> T generateStructured(String prompt, Class<T> responseType) {
        try {
            return chatClient.prompt().user(prompt).call().entity(responseType);
        } catch (Exception e) {
            throw translate(e);
        }
    }

    private OllamaServiceException translate(Exception e) {
        Throwable root = rootCause(e);

        if (root instanceof ConnectException) {
            log.warn("Ollama unavailable: {}", root.getMessage());
            return new OllamaServiceException(OllamaServiceException.Code.OLLAMA_UNAVAILABLE,
                    "Ollama is unavailable", e);
        }
        if (root instanceof SocketTimeoutException || root instanceof TimeoutException) {
            log.warn("Ollama timed out: {}", root.getMessage());
            return new OllamaServiceException(OllamaServiceException.Code.OLLAMA_TIMEOUT,
                    "Ollama request timed out", e);
        }
        // Anything else (malformed JSON from entity(), parse failures, unexpected
        // response shape) is treated as an invalid response rather than crashing
        // the request - matches the spec's "never trust the LLM as sole source
        // of truth" and "never crash on AI failure" rules.
        log.warn("Ollama returned an unusable response: {}", e.getMessage());
        return new OllamaServiceException(OllamaServiceException.Code.OLLAMA_INVALID_RESPONSE,
                "Ollama returned an invalid response", e);
    }

    private Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
