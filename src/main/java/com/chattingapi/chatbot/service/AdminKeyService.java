package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.dto.AdminApiKeyResponse;
import com.chattingapi.chatbot.entity.User;
import com.chattingapi.chatbot.exception.NotFoundException;
import com.chattingapi.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class AdminKeyService {

    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int RAW_KEY_LENGTH = 48;

    private final UserRepository userRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AdminApiKeyResponse issueApiKey() {
        String rawKey = generateRawKey();
        String hashed = apiKeyHasher.hash(rawKey);
        User user = userRepository.save(User.create(hashed));
        return new AdminApiKeyResponse(user.getId(), rawKey);
    }

    @Transactional
    public AdminApiKeyResponse rotateApiKey(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String rawKey = generateRawKey();
        user.updateApiKey(apiKeyHasher.hash(rawKey));
        return new AdminApiKeyResponse(user.getId(), rawKey);
    }

    @Transactional
    public void revokeApiKey(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
        userRepository.deleteById(userId);
    }

    private String generateRawKey() {
        StringBuilder sb = new StringBuilder("cka_");
        for (int i = 0; i < RAW_KEY_LENGTH; i++) {
            sb.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
