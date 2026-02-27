package com.chattingapi.chatbot.controller;

import com.chattingapi.chatbot.dto.ApiResponse;
import com.chattingapi.chatbot.dto.ChatRequest;
import com.chattingapi.chatbot.dto.ConversationDto;
import com.chattingapi.chatbot.dto.MessageDto;
import com.chattingapi.chatbot.dto.PageResult;
import com.chattingapi.chatbot.exception.UnauthorizedException;
import com.chattingapi.chatbot.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat/completions")
    @Operation(summary = "Generate assistant reply")
    public ResponseEntity<ApiResponse<MessageDto>> chat(
            @RequestHeader(name = "X-API-Key", required = false) String apiKey,
            @Valid @RequestBody ChatRequest request
    ) {
        requireApiKey(apiKey);
        MessageDto response = chatService.processChat(apiKey, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/conversations")
    @Operation(summary = "List conversations")
    public ResponseEntity<ApiResponse<PageResult<ConversationDto>>> getConversations(
            @RequestHeader(name = "X-API-Key", required = false) String apiKey,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        requireApiKey(apiKey);
        return ResponseEntity.ok(ApiResponse.success(chatService.getConversations(apiKey, page, size)));
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "List messages in a conversation")
    public ResponseEntity<ApiResponse<PageResult<MessageDto>>> getMessages(
            @RequestHeader(name = "X-API-Key", required = false) String apiKey,
            @PathVariable @Min(1) Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        requireApiKey(apiKey);
        return ResponseEntity.ok(ApiResponse.success(chatService.getMessages(apiKey, id, page, size)));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Delete a conversation")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @RequestHeader(name = "X-API-Key", required = false) String apiKey,
            @PathVariable @Min(1) Long id
    ) {
        requireApiKey(apiKey);
        chatService.deleteConversation(apiKey, id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get conversation detail")
    public ResponseEntity<ApiResponse<ConversationDto>> getConversation(
            @RequestHeader(name = "X-API-Key", required = false) String apiKey,
            @PathVariable @Min(1) Long id
    ) {
        requireApiKey(apiKey);
        return ResponseEntity.ok(ApiResponse.success(chatService.getConversation(apiKey, id)));
    }

    private void requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UnauthorizedException("X-API-Key required");
        }
    }
}
