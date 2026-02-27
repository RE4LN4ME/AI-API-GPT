package com.chattingapi.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminApiKeyResponse {
    private Long userId;
    private String apiKey;
}
