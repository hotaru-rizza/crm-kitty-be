package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.ClientBalanceEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClientBalanceEntryRepository extends JpaRepository<ClientBalanceEntry, UUID> {

    Page<ClientBalanceEntry> findByClientIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID clientId,
            Pageable pageable);
}
