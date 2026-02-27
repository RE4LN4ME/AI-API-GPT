package com.chattingapi.chatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class UserRegistrationResponse {
    private Long userId;
    private String apiKey;
}
