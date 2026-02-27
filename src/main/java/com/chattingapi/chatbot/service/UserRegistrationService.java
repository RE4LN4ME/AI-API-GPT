package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.dto.UserRegistrationResponse;
import com.chattingapi.chatbot.entity.User;
import com.chattingapi.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public UserRegistrationResponse register() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String rawApiKey = generateApiKey();
            String hashedApiKey = apiKeyHasher.hash(rawApiKey);

            if (userRepository.findByApiKey(hashedApiKey).isPresent()) {
                continue;
            }

            User saved = userRepository.saveAndFlush(User.create(hashedApiKey));
            return UserRegistrationResponse.of(saved.getId(), rawApiKey);
        }

        throw new IllegalStateException("Failed to generate unique API key");
    }

    private String generateApiKey() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return "ak_" + token;
    }
}
