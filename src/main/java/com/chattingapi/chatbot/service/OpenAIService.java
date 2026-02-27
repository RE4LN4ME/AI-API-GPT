package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.entity.Message;
import com.chattingapi.chatbot.exception.ErrorCode;
import com.chattingapi.chatbot.exception.UpstreamException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIService {

    private final WebClient webClient;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.timeout-seconds:30}")
    private long timeoutSeconds;

    @Value("${openai.max-retries:2}")
    private int maxRetries;

    @Value("${openai.retry-delay-ms:500}")
    private long retryDelayMs;

    @Value("${openai.fallback-on-rate-limited:false}")
    private boolean fallbackOnRateLimited;

    @Value("${openai.fallback-message:AI response is temporarily unavailable. Please try again shortly.}")
    private String fallbackMessage;

    public String chat(List<Message> contextMessages) {
        String token = apiKey == null ? "" : apiKey.trim();
        if (token.isBlank()) {
            throw new UpstreamException("OpenAI API key is empty");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a helpful assistant."));

        for (Message m : contextMessages) {
            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.7
        );

        Map<String, Object> resp = null;
        UpstreamException lastException = null;
        int totalAttempts = Math.max(1, maxRetries + 1);
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                resp = requestChatCompletion(token, body);
                break;
            } catch (UpstreamException e) {
                if (e.getErrorCode() == ErrorCode.RATE_LIMITED && fallbackOnRateLimited) {
                    log.warn("OpenAI rate limited; fallback response returned");
                    return fallbackMessage;
                }
                lastException = e;
                boolean retriable = e.getErrorCode() == ErrorCode.UPSTREAM_ERROR;
                if (!retriable || attempt == totalAttempts) {
                    throw e;
                }
                log.warn("OpenAI retry attempt={}/{} reason={}", attempt, totalAttempts, e.getMessage());
                sleepRetryDelay();
            }
        }

        if (resp == null && lastException != null) {
            throw lastException;
        }

        if (resp == null) {
            throw new UpstreamException("OpenAI API empty response");
        }

        Object choicesObj = resp.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new UpstreamException("OpenAI API invalid response format");
        }

        Object firstChoiceObj = choices.get(0);
        if (!(firstChoiceObj instanceof Map<?, ?> firstChoice)) {
            throw new UpstreamException("OpenAI API invalid response format");
        }

        Object messageObj = firstChoice.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new UpstreamException("OpenAI API invalid response format");
        }

        Object contentObj = messageMap.get("content");
        if (!(contentObj instanceof String content) || content.isBlank()) {
            throw new UpstreamException("OpenAI API invalid response format");
        }

        return content;
    }

    private String summarize(String body) {
        if (body == null || body.isBlank()) {
            return "empty error body";
        }
        String compact = body.replace("\n", " ").replace("\r", " ").trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "..." : compact;
    }

    private String sanitize(String body) {
        String summarized = summarize(body);
        return summarized.replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
    }

    private Map<String, Object> requestChatCompletion(String token, Map<String, Object> body) {
        try {
            return webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                            status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> {
                                        int code = clientResponse.statusCode().value();
                                        log.warn("OpenAI API error status={} body={}", code, sanitize(errorBody));
                                        if (code == 429) {
                                            return Mono.error(new UpstreamException(
                                                    HttpStatus.TOO_MANY_REQUESTS,
                                                    ErrorCode.RATE_LIMITED,
                                                    "OpenAI quota exceeded"
                                            ));
                                        }
                                        return Mono.error(new UpstreamException(
                                                "OpenAI API error: " + code + " " + summarize(errorBody)
                                        ));
                                    })
                    )
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(Math.max(5, timeoutSeconds)));
        } catch (WebClientRequestException e) {
            log.warn("OpenAI API network error: {}", e.getMessage());
            throw new UpstreamException("OpenAI API network error");
        } catch (UpstreamException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenAI API unexpected error: {}", e.getMessage());
            throw new UpstreamException("OpenAI API call failed");
        }
    }

    private void sleepRetryDelay() {
        try {
            Thread.sleep(Math.max(0, retryDelayMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("OpenAI retry interrupted");
        }
    }
}
