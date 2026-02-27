package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.dto.ChatRequest;
import com.chattingapi.chatbot.dto.ConversationDto;
import com.chattingapi.chatbot.dto.MessageDto;
import com.chattingapi.chatbot.dto.PageResult;
import com.chattingapi.chatbot.entity.Conversation;
import com.chattingapi.chatbot.entity.Message;
import com.chattingapi.chatbot.entity.User;
import com.chattingapi.chatbot.exception.NotFoundException;
import com.chattingapi.chatbot.exception.UnauthorizedException;
import com.chattingapi.chatbot.repository.ConversationRepository;
import com.chattingapi.chatbot.repository.MessageRepository;
import com.chattingapi.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OpenAIService openAIService;
    private final ApiKeyHasher apiKeyHasher;
    private final TransactionTemplate transactionTemplate;
    private final RateLimitService rateLimitService;

    public MessageDto processChat(String apiKey, ChatRequest request) {
        enforceRateLimit(apiKey);
        User user = findUserByApiKey(apiKey);

        Long conversationId = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Conversation conversation;
            if (request.getConversationId() != null) {
                conversation = conversationRepository.findByIdAndUserId(request.getConversationId(), user.getId())
                        .orElseThrow(() -> new NotFoundException("Conversation not found"));
            } else {
                String title = makeTitle(request.getMessage());
                conversation = conversationRepository.save(Conversation.create(user, title));
            }
            messageRepository.save(Message.of(conversation, "user", request.getMessage()));
            return conversation.getId();
        }));

        List<Message> context = Objects.requireNonNull(transactionTemplate.execute(status -> {
            List<Message> ctx = messageRepository
                    .findTop10ByConversationIdAndConversationUserIdOrderByCreatedAtDesc(conversationId, user.getId());
            Collections.reverse(ctx);
            return ctx;
        }));

        String ai = openAIService.chat(context);

        Message saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                    .orElseThrow(() -> new NotFoundException("Conversation not found"));
            return messageRepository.save(Message.of(conversation, "assistant", ai));
        }));

        return MessageDto.fromEntity(saved);
    }

    public SseEmitter processChatStream(String apiKey, ChatRequest request) {
        enforceRateLimit(apiKey);
        User user = findUserByApiKey(apiKey);

        Long conversationId = Objects.requireNonNull(transactionTemplate.execute(status -> {
            Conversation conversation;
            if (request.getConversationId() != null) {
                conversation = conversationRepository.findByIdAndUserId(request.getConversationId(), user.getId())
                        .orElseThrow(() -> new NotFoundException("Conversation not found"));
            } else {
                String title = makeTitle(request.getMessage());
                conversation = conversationRepository.save(Conversation.create(user, title));
            }
            messageRepository.save(Message.of(conversation, "user", request.getMessage()));
            return conversation.getId();
        }));

        List<Message> context = Objects.requireNonNull(transactionTemplate.execute(status -> {
            List<Message> ctx = messageRepository
                    .findTop10ByConversationIdAndConversationUserIdOrderByCreatedAtDesc(conversationId, user.getId());
            Collections.reverse(ctx);
            return ctx;
        }));

        SseEmitter emitter = new SseEmitter(0L);
        StringBuilder assistant = new StringBuilder();

        Disposable disposable = openAIService.chatStream(context)
                .subscribe(
                        token -> {
                            assistant.append(token);
                            sendEvent(emitter, "token", token);
                        },
                        error -> {
                            sendEvent(emitter, "error", "stream failed");
                            emitter.complete();
                        },
                        () -> {
                            if (assistant.isEmpty()) {
                                try {
                                    String fallback = openAIService.chat(context);
                                    if (fallback != null && !fallback.isBlank()) {
                                        assistant.append(fallback);
                                        sendEvent(emitter, "token", fallback);
                                    }
                                } catch (Exception ignored) {
                                    sendEvent(emitter, "error", "stream empty and fallback failed");
                                    emitter.complete();
                                    return;
                                }
                            }

                            if (!assistant.isEmpty()) {
                                saveAssistantMessage(conversationId, user.getId(), assistant.toString());
                            }
                            sendEvent(emitter, "done", "[DONE]");
                            emitter.complete();
                        }
                );

        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(() -> {
            disposable.dispose();
            emitter.complete();
        });
        emitter.onError(ignored -> disposable.dispose());

        return emitter;
    }

    @Transactional(readOnly = true)
    public PageResult<ConversationDto> getConversations(String apiKey, int page, int size) {
        enforceRateLimit(apiKey);
        User user = findUserByApiKey(apiKey);
        var result = conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId(), PageRequest.of(page, size))
                .map(ConversationDto::fromEntity);
        return PageResult.from(result);
    }

    @Transactional(readOnly = true)
    public ConversationDto getConversation(String apiKey, Long conversationId) {
        enforceRateLimit(apiKey);
        User user = findUserByApiKey(apiKey);
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        return ConversationDto.fromEntity(conversation);
    }

    @Transactional(readOnly = true)
    public PageResult<MessageDto> getMessages(String apiKey, Long conversationId, int page, int size) {
        enforceRateLimit(apiKey);
        User user = findUserByApiKey(apiKey);
        var result = messageRepository
                .findByConversationIdAndConversationUserIdOrderByCreatedAtAsc(conversationId, user.getId(), PageRequest.of(page, size))
                .map(MessageDto::fromEntity);
        return PageResult.from(result);
    }

    @Transactional
    public void deleteConversation(String apiKey, Long conversationId) {
        enforceRateLimit(apiKey);
        User user = findUserByApiKey(apiKey);
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        conversationRepository.delete(conversation);
    }

    private User findUserByApiKey(String apiKey) {
        String hashedApiKey = apiKeyHasher.hash(apiKey);
        return userRepository.findByApiKey(hashedApiKey)
                .or(() -> userRepository.findByApiKey(apiKey))
                .orElseThrow(() -> new UnauthorizedException("Invalid API key"));
    }

    private String makeTitle(String message) {
        String m = message.strip();
        return m.substring(0, Math.min(50, m.length()));
    }

    private void enforceRateLimit(String apiKey) {
        rateLimitService.checkOrThrow(apiKey);
    }

    private void saveAssistantMessage(Long conversationId, Long userId, String content) {
        transactionTemplate.executeWithoutResult(status -> {
            Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new NotFoundException("Conversation not found"));
            messageRepository.save(Message.of(conversation, "assistant", content));
        });
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name(name)
                            .data(data, MediaType.TEXT_PLAIN)
            );
        } catch (Exception ignored) {
            emitter.complete();
        }
    }
}
