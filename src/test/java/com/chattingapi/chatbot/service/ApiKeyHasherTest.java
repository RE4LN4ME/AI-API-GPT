package com.chattingapi.chatbot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ApiKeyHasherTest {

    private final ApiKeyHasher apiKeyHasher = new ApiKeyHasher();

    @Test
    void hashIsDeterministic() {
        String h1 = apiKeyHasher.hash("test-key");
        String h2 = apiKeyHasher.hash("test-key");
        assertEquals(h1, h2);
    }

    @Test
    void hashDiffersForDifferentInput() {
        String h1 = apiKeyHasher.hash("test-key-1");
        String h2 = apiKeyHasher.hash("test-key-2");
        assertNotEquals(h1, h2);
    }
}
