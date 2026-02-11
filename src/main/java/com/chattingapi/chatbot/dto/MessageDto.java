package com.chattingapi.chatbot.dto;

import com.chattingapi.chatbot.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor(staticName = "from")
public class MessageDto {
    private Long id;
    private String role;
    private String content;
    private OffsetDateTime createdAt;

    public static MessageDto fromEntity(Message m) {
        return MessageDto.from(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
    }
}
