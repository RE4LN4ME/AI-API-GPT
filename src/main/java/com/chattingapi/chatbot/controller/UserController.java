package com.chattingapi.chatbot.controller;

import com.chattingapi.chatbot.dto.ApiResponse;
import com.chattingapi.chatbot.dto.UserRegistrationResponse;
import com.chattingapi.chatbot.service.UserRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserRegistrationService userRegistrationService;

    @PostMapping("/users/register")
    @Operation(summary = "Register user and issue API key", security = @SecurityRequirement(name = ""))
    public ResponseEntity<ApiResponse<UserRegistrationResponse>> registerUser() {
        return ResponseEntity.ok(ApiResponse.success(userRegistrationService.register()));
    }
}
