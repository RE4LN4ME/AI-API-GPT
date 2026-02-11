package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenAIService {

    private final WebClient webClient;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    public String chat(List<Message> contextMessages) {
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

        Map resp = webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        // choices[0].message.content
        var choices = (List<Map>) resp.get("choices");
        var message = (Map) choices.get(0).get("message");
        return (String) message.get("content");
    }
}
