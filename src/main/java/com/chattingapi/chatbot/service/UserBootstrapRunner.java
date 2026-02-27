package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.entity.User;
import com.chattingapi.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class UserBootstrapRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final Environment environment;

    @Value("${app.bootstrap-user.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${app.bootstrap-user.api-key:}")
    private String bootstrapApiKey;

    @Override
    @Transactional
    public void run(String... args) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            log.warn("Bootstrap user hard-disabled in prod profile");
            return;
        }
        if (!bootstrapEnabled) {
            log.info("Bootstrap user skipped: bootstrap disabled");
            return;
        }
        if (bootstrapApiKey == null || bootstrapApiKey.isBlank()) {
            log.info("Bootstrap user skipped: app.bootstrap-user.api-key is empty");
            return;
        }
        String hashed = apiKeyHasher.hash(bootstrapApiKey);
        String preview = hashed.substring(0, Math.min(8, hashed.length()));
        if (userRepository.findByApiKey(hashed).isPresent()) {
            log.info("Bootstrap user already exists: hashPrefix={}", preview);
            return;
        }
        userRepository.saveAndFlush(User.create(hashed));
        log.info("Bootstrap user created: hashPrefix={}", preview);
    }
}
