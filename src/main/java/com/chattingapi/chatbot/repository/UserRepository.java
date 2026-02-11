package com.chattingapi.chatbot.repository;

import com.chattingapi.chatbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByApiKey(String apiKey);
}
