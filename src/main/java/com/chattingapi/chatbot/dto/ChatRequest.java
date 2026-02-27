package com.chattingapi.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {
    @NotBlank
    @Size(max = 4000)
    private String message;

    @Positive
    private Long conversationId;
}
