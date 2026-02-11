package com.chattingapi.chatbot.repository;

import com.chattingapi.chatbot.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    // 최근 10개 컨텍스트
    List<Message> findTop10ByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
