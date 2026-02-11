package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.dto.ChatRequest;
import com.chattingapi.chatbot.dto.ConversationDto;
import com.chattingapi.chatbot.dto.MessageDto;
import com.chattingapi.chatbot.entity.Conversation;
import com.chattingapi.chatbot.entity.Message;
import com.chattingapi.chatbot.entity.User;
import com.chattingapi.chatbot.exception.NotFoundException;
import com.chattingapi.chatbot.repository.ConversationRepository;
import com.chattingapi.chatbot.repository.MessageRepository;
import com.chattingapi.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OpenAIService openAIService;

    @Transactional
    public MessageDto processChat(String apiKey, ChatRequest request) {
        User user = userRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new NotFoundException("User not found")); // 임시 처리

        Conversation conversation;
        if (request.getConversationId() != null) {
            conversation = conversationRepository.findById(request.getConversationId())
                    .orElseThrow(() -> new NotFoundException("Conversation not found"));
            // TODO: conversation.user.id == user.id 체크 추가.
        } else {
            String title = makeTitle(request.getMessage());
            conversation = conversationRepository.save(Conversation.create(user, title));
        }

        // user 메시지 저장
        messageRepository.save(Message.of(conversation, "user", request.getMessage()));

        // 최근 10개 컨텍스트 구성
        List<Message> ctx = messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(conversation.getId());
        Collections.reverse(ctx);

        // OpenAI 호출
        String ai = openAIService.chat(ctx);

        // assistant 저장
        Message saved = messageRepository.save(Message.of(conversation, "assistant", ai));

        return MessageDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> getConversations(String apiKey) {
        User user = userRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream().map(ConversationDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageDto> getMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream().map(MessageDto::fromEntity).toList();
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        conversationRepository.deleteById(conversationId);
    }

    private String makeTitle(String message) {
        String m = message.strip();
        return m.substring(0, Math.min(50, m.length()));
    }
}
