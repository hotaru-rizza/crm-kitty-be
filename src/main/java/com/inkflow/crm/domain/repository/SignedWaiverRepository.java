package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.SignedWaiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SignedWaiverRepository extends JpaRepository<SignedWaiver, UUID> {
    Optional<SignedWaiver> findByAppointmentId(UUID appointmentId);
    boolean existsByAppointmentId(UUID appointmentId);
}
