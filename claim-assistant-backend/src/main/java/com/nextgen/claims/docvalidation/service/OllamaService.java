package com.nextgen.claims.docvalidation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private final ChatClient chatClient;

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

            return response;

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

    public <T> T generateStructured(
            String prompt,
            Class<T> responseType) {

        long start = System.currentTimeMillis();

        try {

            OllamaOptions options =
                    OllamaOptions.builder()
                            .model("qwen3.5:2b")
                            .temperature(0.0)
                            .numPredict(180)
                            .numCtx(4096)
                            .keepAlive("10m")
                            .format("json")
                            .build();

            log.info(
                    "[OllamaService] START structured promptChars={} model=qwen3.5:2b",
                    prompt.length()
            );

            T response =
                    chatClient
                            .prompt()
                            .options(options)
                            .user(prompt)
                            .call()
                            .entity(responseType);

            long elapsed =
                    System.currentTimeMillis() - start;

            log.info(
                    "[OllamaService] END structured durationMs={}",
                    elapsed
            );

            if (response == null) {

                throw new IllegalStateException(
                        "Ollama returned empty response"
                );
            }

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

    private OllamaServiceException translate(Exception e) {

        Throwable root = rootCause(e);

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

    private Throwable rootCause(Throwable t) {

        Throwable cause = t;

        while (cause.getCause() != null
                && cause.getCause() != cause) {

            cause = cause.getCause();
        }

        return cause;
    }
}