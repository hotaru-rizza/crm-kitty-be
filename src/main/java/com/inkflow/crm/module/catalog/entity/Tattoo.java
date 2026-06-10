package com.inkflow.crm.module.catalog.entity;

import com.inkflow.crm.module.catalog.type.VectorType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tattoos", indexes = {
    @Index(name = "idx_tattoos_staff_id", columnList = "staff_id"),
})
@Getter
@Setter
public class Tattoo {

    public static final String SOURCE_UNSPLASH = "unsplash";
    public static final String SOURCE_PORTFOLIO = "portfolio";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(nullable = false, length = 50)
    private String source = SOURCE_UNSPLASH;

    @Column(name = "source_id")
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TattooStatus status = TattooStatus.READY;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "blur_hash", length = 100)
    private String blurHash;

    @Column(name = "dominant_color", length = 7)
    private String dominantColor;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_url", columnDefinition = "TEXT")
    private String authorUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "alt_description", columnDefinition = "TEXT")
    private String altDescription;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]")
    private String[] tags = new String[0];

    @Type(VectorType.class)
    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean showcase = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public String buildEmbedText() {
        StringBuilder sb = new StringBuilder();
        if (altDescription != null) sb.append(altDescription).append(". ");
        if (description != null) sb.append(description).append(". ");
        if (tags != null && tags.length > 0) {
            sb.append("Tags: ").append(String.join(", ", tags));
        }
        return sb.toString().trim();
    }
}
