package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {

    Page<Service> findByDeletedAtIsNull(Pageable pageable);

    List<Service> findByDeletedAtIsNull();

    Optional<Service> findByIdAndDeletedAtIsNull(UUID id);

    List<Service> findByIsActiveAndDeletedAtIsNull(Boolean isActive);

    List<Service> findByIsActiveTrueAndDeletedAtIsNull();

    long countByIsActiveTrueAndDeletedAtIsNull();
}
