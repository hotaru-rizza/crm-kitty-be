package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<Request, UUID>, JpaSpecificationExecutor<Request> {

    Page<Request> findByStatus(RequestStatus status, Pageable pageable);

    Page<Request> findBySource(RequestSource source, Pageable pageable);

    long countByStatus(RequestStatus status);

    @EntityGraph(attributePaths = {"assignedStaff"})
    List<Request> findByConsumerUserIdOrderByCreatedAtDesc(UUID consumerUserId);

    @Query("SELECT r FROM Request r WHERE r.id = :id")
    Optional<Request> findVisibleById(@Param("id") UUID id);
}
