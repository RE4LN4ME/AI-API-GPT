package com.chattingapi.chatbot.dto;

import com.chattingapi.chatbot.entity.Conversation;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor(staticName = "from")
public class ConversationDto {
    private Long id;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static ConversationDto fromEntity(Conversation c) {
        return ConversationDto.from(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
