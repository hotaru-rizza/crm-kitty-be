package com.inkflow.crm.module.consumer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "consumer_users")
@Getter
@Setter
@NoArgsConstructor
public class ConsumerUser {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "ai_tokens")
    private int aiTokens = 5;

    @ElementCollection
    @CollectionTable(name = "consumer_saved_tattoos", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "tattoo_id")
    private List<Long> savedTattooIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "consumer_saved_artists", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "artist_id")
    private List<String> savedArtistIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "consumer_favorite_generations", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "generation_id")
    private List<UUID> favoriteGenerationIds = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public ConsumerUser(UUID id, String email, String name) {
        this.id = id;
        this.email = email;
        this.name = name;
    }
}
