package com.inkflow.crm.module.catalog.repository;

import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TattooRepository extends JpaRepository<Tattoo, Long> {

    boolean existsBySourceAndSourceId(String source, String sourceId);

    boolean existsBySourceId(String sourceId);

    List<Tattoo> findByStaffIdOrderBySortOrderAscCreatedAtDesc(UUID staffId);

    @Query(value = """
            SELECT * FROM tattoos
            WHERE id != :id
            ORDER BY embedding <=> (SELECT embedding FROM tattoos WHERE id = :id)
            LIMIT :limit
            """, nativeQuery = true)
    List<Tattoo> findSimilar(@Param("id") Long id, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM tattoos
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Tattoo> findByEmbedding(@Param("embedding") String embedding, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM tattoos
            WHERE status = :status
              AND (:tag IS NULL OR :tag = ANY(tags))
              AND (:author IS NULL OR author_name = :author)
              AND (:staffId IS NULL OR staff_id = CAST(:staffId AS uuid))
            ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM tattoos
            WHERE status = :status
              AND (:tag IS NULL OR :tag = ANY(tags))
              AND (:author IS NULL OR author_name = :author)
              AND (:staffId IS NULL OR staff_id = CAST(:staffId AS uuid))
            """,
            nativeQuery = true)
    Page<Tattoo> findByTagOrAll(@Param("tag") String tag, @Param("author") String author,
                                @Param("staffId") String staffId, @Param("status") String status,
                                Pageable pageable);

    List<Tattoo> findAllByIdIn(List<Long> ids);

    long countByStaffIdAndShowcase(UUID staffId, boolean showcase);

    List<Tattoo> findByStaffIdInAndShowcaseTrueOrderBySortOrderAsc(Collection<UUID> staffIds);

    List<Tattoo> findByStaffIdInAndStatusOrderBySortOrderAscCreatedAtDesc(
            Collection<UUID> staffIds, TattooStatus status);

    List<Tattoo> findByStaffIdAndShowcaseTrueOrderBySortOrderAsc(UUID staffId);

    @Query(value = """
            SELECT DISTINCT ON (tag) tag,
                   COALESCE(NULLIF(thumbnail_url, ''), image_url) AS cover_url
            FROM tattoos, unnest(tags) AS tag
            WHERE status = 'READY'
              AND tag IN (:slugs)
            ORDER BY tag, created_at DESC
            """, nativeQuery = true)
    List<StyleCoverView> findCoverUrlsByTags(@Param("slugs") List<String> slugs);
}
