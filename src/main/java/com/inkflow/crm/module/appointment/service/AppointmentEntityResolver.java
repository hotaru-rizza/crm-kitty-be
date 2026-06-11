package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.service.service.ServiceLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppointmentEntityResolver {

    private final AppointmentRepository appointmentRepository;
    private final ClientRepository clientRepository;
    private final StaffRepository staffRepository;
    private final LocationRepository locationRepository;
    private final ProjectRepository projectRepository;
    private final ServiceLookup serviceLookup;

    public Appointment requireAppointment(UUID tenantId, UUID id) {
        return appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(id.toString()));
    }

    public Client requireClient(UUID tenantId, UUID id) {
        return clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(id.toString()));
    }

    public Staff requireStaff(UUID tenantId, UUID id) {
        return staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(id.toString()));
    }

    public Service requireService(UUID tenantId, UUID id) {
        return serviceLookup.require(tenantId, id);
    }

    public Location requireLocation(UUID tenantId, UUID id) {
        return locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.location(id.toString()));
    }

    public Project requireProject(UUID tenantId, UUID id) {
        return projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.project(id.toString()));
    }
}
