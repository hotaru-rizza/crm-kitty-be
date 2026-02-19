package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.MonobankInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonobankInvoiceRepository extends JpaRepository<MonobankInvoice, UUID> {

    Optional<MonobankInvoice> findByMonobankInvoiceId(String monobankInvoiceId);

    Optional<MonobankInvoice> findByAppointmentIdAndStatus(UUID appointmentId, String status);
}
