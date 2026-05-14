package com.inkflow.crm.module.catalog.entity;

import com.inkflow.crm.module.catalog.type.VectorType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Table(name = "tattoos")
@Getter
@Setter
public class Tattoo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String source = "unsplash";

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "thumbnail_url", nullable = false, columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    @Column(name = "blur_hash", length = 100)
    private String blurHash;

    @Column(name = "dominant_color", length = 7)
    private String dominantColor;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_url", columnDefinition = "TEXT")
    private String authorUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "alt_description", columnDefinition = "TEXT")
    private String altDescription;

    @Column(columnDefinition = "TEXT[]")
    private String[] tags = new String[0];

    @Type(VectorType.class)
    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
