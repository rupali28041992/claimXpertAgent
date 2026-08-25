package com.nextgen.claims.docvalidation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.ollama.chat.model}")
    private String chatModel;

    @Retryable(retryFor = OllamaServiceException.class, maxAttempts = 2,
               backoff = @Backoff(delay = 4000))
    public String generate(String prompt) {

        long start = System.currentTimeMillis();

        try {

            String response =
                    chatClient
                            .prompt()
                            .user(prompt)
                            .call()
                            .content();

            long elapsed =
                    System.currentTimeMillis() - start;

            log.info(
                    "[OllamaService] generate completed in {} ms",
                    elapsed
            );

            if (response == null || response.isBlank()) {

                throw new IllegalStateException(
                        "Ollama returned empty response"
                );
            }

            return response.trim();

        } catch (Exception e) {

            long elapsed =
                    System.currentTimeMillis() - start;

            log.warn(
                    "[OllamaService] generate failed after {} ms: {}",
                    elapsed,
                    e.getMessage()
            );

            throw translate(e);
        }
    }

    /**
     * Calls Ollama once and converts the returned JSON manually.
     *
     * IMPORTANT:
     * Do NOT use .entity(responseType) here.
     * That invokes Spring AI BeanOutputConverter and is the
     * source of the empty-response parsing error in the current flow.
     */
    @Retryable(retryFor = OllamaServiceException.class, maxAttempts = 2,
               backoff = @Backoff(delay = 4000))
    public <T> T generateStructured(
            String prompt,
            Class<T> responseType) {

        long start = System.currentTimeMillis();

        try {

            OllamaOptions options =
                    OllamaOptions.builder()
                            .model(chatModel)
                            .temperature(0.0)
                            .numPredict(1024)
                            .numCtx(4096)
                            .keepAlive("10m")
                            .format("json")
                            .build();

            log.info(
                    "[OllamaService] START structured promptChars={} model={}",
                    prompt == null ? 0 : prompt.length(),
                    options.getModel()
            );

            /*
             * IMPORTANT:
             *
             * Use .content(), NOT .entity(responseType).
             *
             * This prevents Spring AI's BeanOutputConverter from
             * trying to deserialize an empty response.
             */
            String content =
                    chatClient
                            .prompt()
                            .options(options)
                            .user(prompt)
                            .call()
                            .content();

            long elapsed =
                    System.currentTimeMillis() - start;

            log.info(
                    "[OllamaService] RAW structured response received after {} ms, responseChars={}",
                    elapsed,
                    content == null ? 0 : content.length()
            );

            if (content == null || content.isBlank()) {

                throw new IllegalStateException(
                        "Ollama returned empty response"
                );
            }

            String json =
                    cleanJsonResponse(content);

            log.debug(
                    "[OllamaService] cleaned JSON response={}",
                    json
            );

            /*
             * Convert JSON using Jackson directly.
             */
            T response =
                    objectMapper.readValue(
                            json,
                            responseType
                    );

            log.info(
                    "[OllamaService] END structured durationMs={}",
                    System.currentTimeMillis() - start
            );

            return response;

        } catch (Exception e) {

            long elapsed =
                    System.currentTimeMillis() - start;

            log.warn(
                    "[OllamaService] structured call failed after {} ms: {}",
                    elapsed,
                    e.getMessage()
            );

            throw translate(e);
        }
    }

    /**
     * Strips Qwen3 <think>...</think> reasoning blocks and markdown code
     * fences, leaving only the JSON payload.
     */
    private String cleanJsonResponse(String content) {

        String json = content.trim();

        // Qwen3 "thinking" models wrap chain-of-thought in <think>...</think>
        // before emitting the actual answer. Strip it so Jackson sees clean JSON.
        int thinkEnd = json.lastIndexOf("</think>");
        if (thinkEnd != -1) {
            json = json.substring(thinkEnd + "</think>".length()).trim();
        }

        if (json.startsWith("```json")) {
            json = json.substring("```json".length()).trim();
        } else if (json.startsWith("```")) {
            json = json.substring("```".length()).trim();
        }

        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3).trim();
        }

        // Find the first { and last } to extract the JSON object in case
        // the model prefixed or suffixed extra prose.
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            json = json.substring(start, end + 1);
        }

        return json;
    }

    private OllamaServiceException translate(
            Exception e) {

        Throwable root =
                rootCause(e);

        if (root instanceof ConnectException) {

            return new OllamaServiceException(
                    OllamaServiceException.Code.OLLAMA_UNAVAILABLE,
                    "Ollama is unavailable",
                    e
            );
        }

        if (root instanceof SocketTimeoutException
                || root instanceof TimeoutException) {

            return new OllamaServiceException(
                    OllamaServiceException.Code.OLLAMA_TIMEOUT,
                    "Ollama request timed out",
                    e
            );
        }

        log.warn(
                "[OllamaService] Ollama returned unusable response: {}",
                root.getMessage()
        );

        return new OllamaServiceException(
                OllamaServiceException.Code.OLLAMA_INVALID_RESPONSE,
                "Ollama returned an invalid response",
                e
        );
    }

    private Throwable rootCause(
            Throwable t) {

        Throwable cause =
                t;

        while (cause.getCause() != null
                && cause.getCause() != cause) {

            cause =
                    cause.getCause();
        }

        return cause;
    }
}