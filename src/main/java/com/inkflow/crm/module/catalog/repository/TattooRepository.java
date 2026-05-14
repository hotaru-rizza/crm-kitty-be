package com.inkflow.crm.module.catalog.repository;

import com.inkflow.crm.module.catalog.entity.Tattoo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TattooRepository extends JpaRepository<Tattoo, Long> {

    boolean existsBySourceAndSourceId(String source, String sourceId);

    boolean existsBySourceId(String sourceId);

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
            WHERE (:tag IS NULL OR :tag = ANY(tags))
              AND (:author IS NULL OR author_name = :author)
            ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM tattoos
            WHERE (:tag IS NULL OR :tag = ANY(tags))
              AND (:author IS NULL OR author_name = :author)
            """,
            nativeQuery = true)
    Page<Tattoo> findByTagOrAll(@Param("tag") String tag, @Param("author") String author, Pageable pageable);

    List<Tattoo> findAllByIdIn(List<Long> ids);
}
