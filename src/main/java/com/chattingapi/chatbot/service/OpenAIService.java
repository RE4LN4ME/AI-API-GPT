package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.entity.Message;
import com.chattingapi.chatbot.exception.ErrorCode;
import com.chattingapi.chatbot.exception.UpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    public Flux<String> chatStream(List<Message> contextMessages) {
        String token = apiKey == null ? "" : apiKey.trim();
        if (token.isBlank()) {
            return Flux.error(new UpstreamException("OpenAI API key is empty"));
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are a helpful assistant."));
        for (Message m : contextMessages) {
            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.7,
                "stream", true
        );

        Flux<String> raw = webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(errorBody -> Mono.error(toUpstreamException(clientResponse.statusCode().value(), errorBody)))
                )
                .bodyToFlux(String.class);

        return decodeOpenAiSse(raw);
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
                                        return Mono.error(toUpstreamException(code, errorBody));
                                    })
                    )
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(Math.max(5, timeoutSeconds)));
        } catch (WebClientRequestException e) {
            log.warn("OpenAI API network error: {}", e.getMessage());
            throw new UpstreamException("OpenAI API network error");
        } catch (WebClientResponseException e) {
            throw toUpstreamException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (UpstreamException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenAI API unexpected error: {}", e.getMessage());
            throw new UpstreamException("OpenAI API call failed");
        }
    }

    private Flux<String> decodeOpenAiSse(Flux<String> raw) {
        return Flux.create(sink -> {
            StringBuilder buffer = new StringBuilder();

            raw.subscribe(
                    chunk -> {
                        buffer.append(chunk);
                        emitCompletedLines(buffer, sink);
                    },
                    sink::error,
                    () -> {
                        if (!buffer.isEmpty()) {
                            handleSseLine(buffer.toString().trim(), sink);
                        }
                        sink.complete();
                    }
            );
        });
    }

    private void emitCompletedLines(StringBuilder buffer, FluxSink<String> sink) {
        int newLineIndex;
        while ((newLineIndex = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, newLineIndex).trim();
            buffer.delete(0, newLineIndex + 1);
            handleSseLine(line, sink);
        }
    }

    private void handleSseLine(String line, FluxSink<String> sink) {
        if (line.isBlank() || !line.startsWith("data:")) {
            return;
        }

        String payload = line.substring(5).trim();
        if ("[DONE]".equals(payload)) {
            sink.complete();
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode contentNode = root.path("choices").path(0).path("delta").path("content");
            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                String token = contentNode.asText("");
                if (!token.isBlank()) {
                    sink.next(token);
                }
            }
        } catch (Exception e) {
            log.debug("OpenAI stream chunk parse skipped: {}", e.getMessage());
        }
    }

    private UpstreamException toUpstreamException(int code, String errorBody) {
        log.warn("OpenAI API error status={} body={}", code, sanitize(errorBody));
        if (code == 429) {
            return new UpstreamException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.RATE_LIMITED,
                    "OpenAI quota exceeded"
            );
        }
        return new UpstreamException("OpenAI API error: " + code + " " + summarize(errorBody));
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
