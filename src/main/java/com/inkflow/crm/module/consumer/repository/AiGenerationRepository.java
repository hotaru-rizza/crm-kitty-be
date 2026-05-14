package com.inkflow.crm.module.consumer.repository;

import com.inkflow.crm.module.consumer.entity.AiGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiGenerationRepository extends JpaRepository<AiGeneration, UUID> {
    List<AiGeneration> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
