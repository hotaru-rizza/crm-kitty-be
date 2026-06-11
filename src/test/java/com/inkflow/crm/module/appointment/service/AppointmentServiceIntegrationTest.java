package com.inkflow.crm.module.appointment.service;

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
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
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
                .status(AppointmentStatus.NEW)
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
    }

    @Test
    void createAppointment_assignsCurrentTenant() {
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
