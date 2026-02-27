package com.chattingapi.chatbot.repository;

import com.chattingapi.chatbot.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdAndConversationUserIdOrderByCreatedAtAsc(Long conversationId, Long userId);
    Page<Message> findByConversationIdAndConversationUserIdOrderByCreatedAtAsc(Long conversationId, Long userId, Pageable pageable);

    // 최근 10개 컨텍스트
    List<Message> findTop10ByConversationIdAndConversationUserIdOrderByCreatedAtDesc(Long conversationId, Long userId);
}
