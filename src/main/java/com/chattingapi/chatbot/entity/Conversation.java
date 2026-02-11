package com.chattingapi.chatbot.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 255)
    private String title;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        var now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public static Conversation create(User user, String title) {
        Conversation c = new Conversation();
        c.user = user;
        c.title = title;
        return c;
    }

    public void touchTitleIfNull(String title) {
        if (this.title == null || this.title.isBlank()) this.title = title;
    }
}
