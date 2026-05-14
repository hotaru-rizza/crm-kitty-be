package com.inkflow.crm.module.consumer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_generations")
@Getter
@Setter
@NoArgsConstructor
public class AiGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private ConsumerUser user;

    @Column(nullable = false)
    private String imageUrl;

    @Column(length = 1000)
    private String prompt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public AiGeneration(ConsumerUser user, String imageUrl, String prompt) {
        this.user = user;
        this.imageUrl = imageUrl;
        this.prompt = prompt;
    }
}
