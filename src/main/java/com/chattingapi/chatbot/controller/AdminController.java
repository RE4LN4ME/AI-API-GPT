package com.chattingapi.chatbot.controller;

import com.chattingapi.chatbot.dto.AdminApiKeyResponse;
import com.chattingapi.chatbot.dto.ApiResponse;
import com.chattingapi.chatbot.exception.UnauthorizedException;
import com.chattingapi.chatbot.service.AdminKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
public class AdminController {

    private final AdminKeyService adminKeyService;

    @Value("${app.admin.api-key:}")
    private String adminApiKey;

    @PostMapping("/keys")
    public ResponseEntity<ApiResponse<AdminApiKeyResponse>> issueApiKey(
            @RequestHeader(name = "X-Admin-Key", required = false) String key
    ) {
        requireAdminKey(key);
        return ResponseEntity.ok(ApiResponse.success(adminKeyService.issueApiKey()));
    }

    @PostMapping("/keys/{userId}/rotate")
    public ResponseEntity<ApiResponse<AdminApiKeyResponse>> rotateApiKey(
            @RequestHeader(name = "X-Admin-Key", required = false) String key,
            @PathVariable Long userId
    ) {
        requireAdminKey(key);
        return ResponseEntity.ok(ApiResponse.success(adminKeyService.rotateApiKey(userId)));
    }

    @DeleteMapping("/keys/{userId}")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(
            @RequestHeader(name = "X-Admin-Key", required = false) String key,
            @PathVariable Long userId
    ) {
        requireAdminKey(key);
        adminKeyService.revokeApiKey(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    private void requireAdminKey(String key) {
        if (adminApiKey == null || adminApiKey.isBlank()) {
            throw new UnauthorizedException("Admin API not configured");
        }
        if (key == null || key.isBlank() || !adminApiKey.equals(key)) {
            throw new UnauthorizedException("Invalid admin key");
        }
    }
}
