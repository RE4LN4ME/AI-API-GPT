package com.chattingapi.chatbot.controller;

import com.chattingapi.chatbot.dto.ApiResponse;
import com.chattingapi.chatbot.dto.ChatRequest;
import com.chattingapi.chatbot.dto.ConversationDto;
import com.chattingapi.chatbot.dto.MessageDto;
import com.chattingapi.chatbot.exception.BadRequestException;
import com.chattingapi.chatbot.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat/completions")
    public ResponseEntity<ApiResponse<MessageDto>> chat(
            @RequestHeader(name = "X-API-Key", required = false) String apiKey,
            @Valid @RequestBody ChatRequest request
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("X-API-Key required");
        }
        MessageDto response = chatService.processChat(apiKey, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationDto>>> getConversations(
            @RequestHeader(name = "X-API-Key", required = false) String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank()) throw new BadRequestException("X-API-Key required");
        return ResponseEntity.ok(ApiResponse.success(chatService.getConversations(apiKey)));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<List<MessageDto>>> getMessages(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getMessages(id)));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConversation(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id)));
    }
}
