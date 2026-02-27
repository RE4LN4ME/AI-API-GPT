package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.entity.User;
import com.chattingapi.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

@Component
@Order(0)
@RequiredArgsConstructor
public class ApiKeyMigrationRunner implements CommandLineRunner {

    private static final Pattern SHA256_HEX = Pattern.compile("^[a-f0-9]{64}$");

    private final UserRepository userRepository;
    private final ApiKeyHasher apiKeyHasher;

    @Override
    @Transactional
    public void run(String... args) {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            String stored = user.getApiKey();
            if (stored == null || SHA256_HEX.matcher(stored).matches()) {
                continue;
            }
            user.updateApiKey(apiKeyHasher.hash(stored));
        }
    }
}
