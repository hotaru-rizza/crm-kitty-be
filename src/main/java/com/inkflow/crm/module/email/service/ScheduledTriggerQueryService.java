package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduledTriggerQueryService {

    private final AppointmentRepository appointmentRepository;
    private final ClientRepository clientRepository;

    public List<Appointment> findAppointmentsByDateRange(Instant from, Instant to) {
        return appointmentRepository.findByDateRange(from, to);
    }

    public List<Client> findClientsByBirthDate(LocalDate birthDate) {
        return clientRepository.findByBirthDateAndDeletedAtIsNull(birthDate);
    }

    public List<Client> findInactiveClients(Instant cutoff) {
        return clientRepository.findInactiveClients(cutoff);
    }
}
