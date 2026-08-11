package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.RequestMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestMessageRepository extends JpaRepository<RequestMessage, UUID> {

    List<RequestMessage> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
