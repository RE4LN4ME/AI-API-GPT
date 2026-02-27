package com.chattingapi.chatbot.service;

import com.chattingapi.chatbot.exception.RateLimitedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitService {

    private final ConcurrentMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.requests-per-window:60}")
    private int requestsPerWindow;

    @Value("${app.rate-limit.window-seconds:60}")
    private long windowSeconds;

    public void checkOrThrow(String apiKey) {
        if (!enabled) {
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowStart = now - (Math.max(1, windowSeconds) * 1000L);
        String key = apiKey.strip();
        Deque<Long> queue = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() < windowStart) {
                queue.pollFirst();
            }
            if (queue.size() >= Math.max(1, requestsPerWindow)) {
                throw new RateLimitedException("Rate limit exceeded");
            }
            queue.addLast(now);
        }
    }

    public void clearAll() {
        windows.clear();
    }
}
