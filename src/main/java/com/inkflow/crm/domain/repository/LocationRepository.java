package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    Page<Location> findByDeletedAtIsNull(Pageable pageable);
    List<Location> findByDeletedAtIsNull();
    Optional<Location> findByIdAndDeletedAtIsNull(UUID id);
    List<Location> findByIsActiveAndDeletedAtIsNull(Boolean isActive);

    long countByDeletedAtIsNull();

    long countByIsActiveAndDeletedAtIsNull(Boolean isActive);

    Optional<Location> findFirstByIsActiveTrueAndDeletedAtIsNull();
}
