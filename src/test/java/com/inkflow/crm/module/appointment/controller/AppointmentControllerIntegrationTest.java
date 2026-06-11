package com.inkflow.crm.module.appointment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AppointmentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Autowired
    private AppointmentRepository appointmentRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getAllAppointments_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/appointments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllAppointments_withOwnerAuth_returnsOk() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/appointments").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createAppointment_withOwnerAuth_returnsCreated() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        CreateAppointmentRequest body = CreateAppointmentRequest.builder()
                .clientId(bundle.client().getId())
                .artistId(bundle.owner().getId())
                .serviceId(bundle.service().getId())
                .locationId(bundle.location().getId())
                .startTime(start)
                .endTime(start.plus(1, ChronoUnit.HOURS))
                .price(BigDecimal.valueOf(1000))
                .build();

        mockMvc.perform(post("/appointments")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.client.id").value(bundle.client().getId().toString()));

        long count = appointmentRepository.findAll().stream()
                .filter(a -> bundle.tenant().getId().equals(a.getTenantId()))
                .count();
        assertEquals(1, count);
    }

    @Test
    void createAppointment_withEmptyBody_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(post("/appointments")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAppointment_cancelsAndPersistsStatusInDb() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        CreateAppointmentRequest createBody = CreateAppointmentRequest.builder()
                .clientId(bundle.client().getId())
                .artistId(bundle.owner().getId())
                .serviceId(bundle.service().getId())
                .locationId(bundle.location().getId())
                .startTime(start)
                .endTime(start.plus(1, ChronoUnit.HOURS))
                .price(BigDecimal.valueOf(1000))
                .build();

        String createResponse = mockMvc.perform(post("/appointments")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID appointmentId = UUID.fromString(
                objectMapper.readTree(createResponse).path("data").path("id").asText());

        UpdateAppointmentRequest updateBody = UpdateAppointmentRequest.builder()
                .status("cancelled")
                .cancellationReason("Schedule conflict")
                .build();

        mockMvc.perform(patch("/appointments/{id}", appointmentId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));

        Appointment persisted = appointmentRepository.findById(appointmentId).orElseThrow();
        assertEquals(AppointmentStatus.CANCELLED, persisted.getStatus());
        assertEquals("Schedule conflict", persisted.getCancellationReason());
    }

    @Test
    void updateAppointment_reschedulesAndPersistsTimesInDb() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant originalStart = Instant.now().plus(4, ChronoUnit.DAYS);
        Instant originalEnd = originalStart.plus(1, ChronoUnit.HOURS);
        UUID appointmentId = createAppointment(bundle, originalStart, originalEnd);

        Instant newStart = originalStart.plus(2, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(90, ChronoUnit.MINUTES);
        UpdateAppointmentRequest updateBody = UpdateAppointmentRequest.builder()
                .startTime(newStart)
                .endTime(newEnd)
                .build();

        mockMvc.perform(patch("/appointments/{id}", appointmentId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startTime").exists());

        Appointment persisted = appointmentRepository.findById(appointmentId).orElseThrow();
        assertEquals(newStart.truncatedTo(ChronoUnit.MILLIS), persisted.getStartTime().truncatedTo(ChronoUnit.MILLIS));
        assertEquals(newEnd.truncatedTo(ChronoUnit.MILLIS), persisted.getEndTime().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void updateAppointment_rescheduleConflict_returnsConflict() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant firstStart = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant firstEnd = firstStart.plus(1, ChronoUnit.HOURS);
        createAppointment(bundle, firstStart, firstEnd);

        Instant secondStart = firstStart.plus(3, ChronoUnit.HOURS);
        Instant secondEnd = secondStart.plus(1, ChronoUnit.HOURS);
        UUID secondAppointmentId = createAppointment(bundle, secondStart, secondEnd);

        UpdateAppointmentRequest conflictBody = UpdateAppointmentRequest.builder()
                .startTime(firstStart.plus(30, ChronoUnit.MINUTES))
                .endTime(firstEnd.plus(30, ChronoUnit.MINUTES))
                .build();

        mockMvc.perform(patch("/appointments/{id}", secondAppointmentId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.error.code").value("TIME_SLOT_CONFLICT"));

        Appointment unchanged = appointmentRepository.findById(secondAppointmentId).orElseThrow();
        assertEquals(secondStart.truncatedTo(ChronoUnit.MILLIS), unchanged.getStartTime().truncatedTo(ChronoUnit.MILLIS));
    }

    private UUID createAppointment(TenantBundle bundle, Instant start, Instant end) throws Exception {
        CreateAppointmentRequest body = CreateAppointmentRequest.builder()
                .clientId(bundle.client().getId())
                .artistId(bundle.owner().getId())
                .serviceId(bundle.service().getId())
                .locationId(bundle.location().getId())
                .startTime(start)
                .endTime(end)
                .price(BigDecimal.valueOf(1000))
                .build();

        String response = mockMvc.perform(post("/appointments")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }
}
