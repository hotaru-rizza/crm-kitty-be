package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.appointment.dto.CreateAppointmentRequest;
import com.inkflow.crm.module.appointment.dto.UpdateAppointmentRequest;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@Transactional
class AppointmentServiceIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private LocationRepository locationRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getAppointmentById_rejectsAppointmentFromAnotherTenant() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        Appointment foreignAppointment = appointmentRepository.save(Appointment.builder()
                .tenantId(tenantB.tenant().getId())
                .client(tenantB.client())
                .artist(tenantB.owner())
                .service(tenantB.service())
                .location(tenantB.location())
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());

        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.getAppointmentById(foreignAppointment.getId())
        );

        assertTrue(appointmentRepository.findById(foreignAppointment.getId()).isPresent());
    }

    @Test
    void createAppointment_assignsCurrentTenantAndPersistsFields() {
        TenantBundle tenant = seedTenant();
        SecurityTestSupport.authenticate(tenant.owner());

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        var created = appointmentService.createAppointment(CreateAppointmentRequest.builder()
                .clientId(tenant.client().getId())
                .artistId(tenant.owner().getId())
                .serviceId(tenant.service().getId())
                .locationId(tenant.location().getId())
                .startTime(start)
                .endTime(end)
                .price(BigDecimal.valueOf(1000))
                .build());

        Appointment saved = appointmentRepository.findById(created.getId()).orElseThrow();
        assertEquals(tenant.tenant().getId(), saved.getTenantId());
        assertEquals(AppointmentStatus.SCHEDULED, saved.getStatus());
        assertEquals(tenant.client().getId(), saved.getClient().getId());
        assertEquals(tenant.owner().getId(), saved.getArtist().getId());
        assertEquals(BigDecimal.valueOf(1000), saved.getFinalPrice());
        assertEquals(start.truncatedTo(ChronoUnit.MILLIS), saved.getStartTime().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void createAppointment_rejectsOverlappingTimeSlot() {
        TenantBundle tenant = seedTenant();
        SecurityTestSupport.authenticate(tenant.owner());

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .clientId(tenant.client().getId())
                .artistId(tenant.owner().getId())
                .serviceId(tenant.service().getId())
                .locationId(tenant.location().getId())
                .startTime(start)
                .endTime(end)
                .price(BigDecimal.valueOf(1000))
                .build();

        appointmentService.createAppointment(request);

        assertThrows(BusinessRuleException.class, () -> appointmentService.createAppointment(request));

        long tenantAppointmentCount = appointmentRepository.findAll().stream()
                .filter(a -> tenant.tenant().getId().equals(a.getTenantId()) && a.getDeletedAt() == null)
                .count();
        assertEquals(1, tenantAppointmentCount);
    }

    @Test
    void updateAppointment_cancelsAndPersistsStatusInDb() {
        TenantBundle tenant = seedTenant();
        SecurityTestSupport.authenticate(tenant.owner());

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        var created = appointmentService.createAppointment(CreateAppointmentRequest.builder()
                .clientId(tenant.client().getId())
                .artistId(tenant.owner().getId())
                .serviceId(tenant.service().getId())
                .locationId(tenant.location().getId())
                .startTime(start)
                .endTime(start.plus(1, ChronoUnit.HOURS))
                .price(BigDecimal.valueOf(1000))
                .build());

        appointmentService.updateAppointment(created.getId(), UpdateAppointmentRequest.builder()
                .status("cancelled")
                .cancellationReason("Client rescheduled")
                .build());

        Appointment persisted = appointmentRepository.findById(created.getId()).orElseThrow();
        assertEquals(AppointmentStatus.CANCELLED, persisted.getStatus());
        assertEquals("Client rescheduled", persisted.getCancellationReason());
    }

    @Test
    void deleteAppointment_softDeletesRecord() {
        TenantBundle tenant = seedTenant();
        SecurityTestSupport.authenticate(tenant.owner());

        Instant start = Instant.now().plus(4, ChronoUnit.DAYS);
        var created = appointmentService.createAppointment(CreateAppointmentRequest.builder()
                .clientId(tenant.client().getId())
                .artistId(tenant.owner().getId())
                .serviceId(tenant.service().getId())
                .locationId(tenant.location().getId())
                .startTime(start)
                .endTime(start.plus(1, ChronoUnit.HOURS))
                .price(BigDecimal.valueOf(1000))
                .build());

        appointmentService.deleteAppointment(created.getId());

        Appointment persisted = appointmentRepository.findById(created.getId()).orElseThrow();
        assertNotNull(persisted.getDeletedAt());
    }

    private TenantBundle seedTenant() {
        return IntegrationTestData.seedTenant(
                tenantRepository,
                staffRepository,
                clientRepository,
                serviceRepository,
                locationRepository
        );
    }
}
