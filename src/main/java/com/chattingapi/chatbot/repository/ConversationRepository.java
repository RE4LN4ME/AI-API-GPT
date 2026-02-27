package com.chattingapi.chatbot.repository;

import com.chattingapi.chatbot.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Page<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);
}
