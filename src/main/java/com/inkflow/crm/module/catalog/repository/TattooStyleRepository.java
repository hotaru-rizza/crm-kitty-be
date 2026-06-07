package com.inkflow.crm.module.catalog.repository;

import com.inkflow.crm.module.catalog.entity.TattooStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TattooStyleRepository extends JpaRepository<TattooStyle, Long> {

    List<TattooStyle> findByActiveTrueOrderBySortOrderAsc();

    Optional<TattooStyle> findBySlug(String slug);
}
