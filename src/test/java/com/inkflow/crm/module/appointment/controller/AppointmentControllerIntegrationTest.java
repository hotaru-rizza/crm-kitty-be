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
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.GalleryPhotoRepository;
import com.inkflow.crm.module.appointment.dto.AddAppointmentPhotoRequest;
import com.inkflow.crm.module.appointment.dto.AppointmentItemRequest;
import com.inkflow.crm.module.appointment.dto.CreateAppointmentRequest;
import com.inkflow.crm.module.appointment.dto.UpdateAppointmentRequest;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private GalleryPhotoRepository galleryPhotoRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RolePermissionService rolePermissionService;

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
        entityManager.flush();
        entityManager.clear();
        Instant expectedStart = appointmentRepository.findById(secondAppointmentId)
                .orElseThrow()
                .getStartTime();

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

        entityManager.flush();
        entityManager.clear();
        Appointment unchanged = appointmentRepository.findById(secondAppointmentId).orElseThrow();
        assertEquals(expectedStart.truncatedTo(ChronoUnit.MILLIS), unchanged.getStartTime().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void getAppointment_byId_returnsDetail() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant start = Instant.now().plus(6, ChronoUnit.DAYS);
        UUID appointmentId = createAppointment(bundle, start, start.plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/appointments/{id}", appointmentId).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.client.id").value(bundle.client().getId().toString()));
    }

    @Test
    void getClientHistory_returnsClientAppointments() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        UUID appointmentId = createAppointment(bundle, start, start.plus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/appointments/client/{clientId}", bundle.client().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(appointmentId.toString()));
    }

    @Test
    void getCalendar_withDateRange_returnsMatchingAppointments() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant start = Instant.now().plus(8, ChronoUnit.DAYS);
        UUID appointmentId = createAppointment(bundle, start, start.plus(1, ChronoUnit.HOURS));

        Instant from = start.minus(1, ChronoUnit.DAYS);
        Instant to = start.plus(2, ChronoUnit.DAYS);

        mockMvc.perform(get("/appointments/calendar")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(appointmentId.toString()));
    }

    @Test
    void getCalendar_withoutDateRange_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/appointments/calendar").with(crmUser(bundle.owner())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAppointment_softDeletesInDb() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant start = Instant.now().plus(9, ChronoUnit.DAYS);
        UUID appointmentId = createAppointment(bundle, start, start.plus(1, ChronoUnit.HOURS));

        mockMvc.perform(delete("/appointments/{id}", appointmentId).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Appointment persisted = appointmentRepository.findById(appointmentId).orElseThrow();
        assertTrue(persisted.isDeleted());
    }

    @Test
    void addPhoto_persistsGalleryPhoto() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        UUID appointmentId = createAppointment(bundle, start, start.plus(1, ChronoUnit.HOURS));

        AddAppointmentPhotoRequest photoBody = new AddAppointmentPhotoRequest();
        photoBody.setUrl("https://example.com/tattoo.jpg");
        photoBody.setStage("fresh");

        mockMvc.perform(post("/appointments/{id}/photos", appointmentId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(photoBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value("https://example.com/tattoo.jpg"))
                .andExpect(jsonPath("$.data.stage").value("fresh"));

        var photos = galleryPhotoRepository.findByAppointmentId(appointmentId);
        assertEquals(1, photos.size());
        assertEquals("https://example.com/tattoo.jpg", photos.get(0).getUrl());
    }

    @Test
    void addPhoto_withBlankUrl_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant start = Instant.now().plus(11, ChronoUnit.DAYS);
        UUID appointmentId = createAppointment(bundle, start, start.plus(1, ChronoUnit.HOURS));

        AddAppointmentPhotoRequest photoBody = new AddAppointmentPhotoRequest();
        photoBody.setUrl("   ");
        photoBody.setStage("fresh");

        mockMvc.perform(post("/appointments/{id}/photos", appointmentId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(photoBody)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addPhoto_withoutEditPermission_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        Instant start = Instant.now().plus(12, ChronoUnit.DAYS);
        UUID appointmentId = createAppointment(bundle, start, start.plus(1, ChronoUnit.HOURS));

        String stripEditPermission = """
                {"permissions":["calendar.view_own","calendar.create","calendar.cancel","clients.view_own"]}
                """;

        mockMvc.perform(put("/settings/roles/artist")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stripEditPermission))
                .andExpect(status().isOk());

        AddAppointmentPhotoRequest photoBody = new AddAppointmentPhotoRequest();
        photoBody.setUrl("https://example.com/tattoo.jpg");
        photoBody.setStage("fresh");

        mockMvc.perform(post("/appointments/{id}/photos", appointmentId)
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(photoBody)))
                .andExpect(status().isForbidden());

        assertTrue(galleryPhotoRepository.findByAppointmentId(appointmentId).isEmpty());
    }

    @Test
    void deletePhoto_removesFromDb() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant start = Instant.now().plus(13, ChronoUnit.DAYS);
        UUID appointmentId = createAppointment(bundle, start, start.plus(1, ChronoUnit.HOURS));

        AddAppointmentPhotoRequest photoBody = new AddAppointmentPhotoRequest();
        photoBody.setUrl("https://example.com/remove-me.jpg");
        photoBody.setStage("sketch");

        String photoResponse = mockMvc.perform(post("/appointments/{id}/photos", appointmentId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(photoBody)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID photoId = UUID.fromString(
                objectMapper.readTree(photoResponse).path("data").path("id").asText());

        mockMvc.perform(delete("/appointments/{id}/photos/{photoId}", appointmentId, photoId)
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(galleryPhotoRepository.findById(photoId).isPresent());
    }

    @Test
    void updateAppointment_artistCanPatchOwnAppointment() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        ensureDefaultPermissions(bundle);
        Staff artistA = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UUID appointmentId = createAppointmentForArtist(bundle, artistA);

        UpdateAppointmentRequest updateBody = UpdateAppointmentRequest.builder()
                .notes("Updated by artist")
                .build();

        mockMvc.perform(patch("/appointments/{id}", appointmentId)
                        .with(crmUser(artistA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").value("Updated by artist"));
    }

    @Test
    void updateAppointment_artistCannotPatchAnotherArtistsAppointment() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        ensureDefaultPermissions(bundle);
        Staff artistA = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        Staff artistB = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UUID appointmentId = createAppointmentForArtist(bundle, artistB);

        UpdateAppointmentRequest updateBody = UpdateAppointmentRequest.builder()
                .notes("Should be denied")
                .build();

        mockMvc.perform(patch("/appointments/{id}", appointmentId)
                        .with(crmUser(artistA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isForbidden());

        Appointment unchanged = appointmentRepository.findById(appointmentId).orElseThrow();
        assertTrue(unchanged.getNotes() == null || !"Should be denied".equals(unchanged.getNotes()));
    }

    @Test
    void getAppointment_artistCannotViewAnotherArtistsAppointment() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        ensureDefaultPermissions(bundle);
        Staff artistA = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        Staff artistB = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UUID appointmentId = createAppointmentForArtist(bundle, artistB);

        mockMvc.perform(get("/appointments/{id}", appointmentId).with(crmUser(artistA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAppointment_adminCanPatchAnyAppointment() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        ensureDefaultPermissions(bundle);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        Staff admin = staffRepository.save(Staff.builder()
                .tenantId(bundle.tenant().getId())
                .firstName("Admin")
                .lastName("User")
                .email("admin-" + UUID.randomUUID() + "@test.com")
                .role(com.inkflow.crm.domain.enums.UserRole.ADMIN)
                .calendarColor("#6366f1")
                .status(com.inkflow.crm.domain.enums.StaffStatus.WORKING)
                .accountStatus(com.inkflow.crm.domain.enums.AccountStatus.ACTIVE)
                .build());

        UUID appointmentId = createAppointmentForArtist(bundle, artist);

        UpdateAppointmentRequest updateBody = UpdateAppointmentRequest.builder()
                .notes("Updated by admin")
                .build();

        mockMvc.perform(patch("/appointments/{id}", appointmentId)
                        .with(crmUser(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").value("Updated by admin"));
    }

    @Test
    void createAppointment_artistCannotAssignOtherArtist() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        ensureDefaultPermissions(bundle);
        Staff artistA = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        Staff artistB = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        Instant start = Instant.now().plus(14, ChronoUnit.DAYS);
        CreateAppointmentRequest body = CreateAppointmentRequest.builder()
                .clientId(bundle.client().getId())
                .artistId(artistB.getId())
                .serviceId(bundle.service().getId())
                .locationId(bundle.location().getId())
                .startTime(start)
                .endTime(start.plus(1, ChronoUnit.HOURS))
                .price(BigDecimal.valueOf(1000))
                .build();

        mockMvc.perform(post("/appointments")
                        .with(crmUser(artistA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAppointment_fromOtherTenant_returnsNotFound() throws Exception {
        TenantBundle tenantA = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        TenantBundle tenantB = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        UUID appointmentId = createAppointment(tenantB, Instant.now().plus(15, ChronoUnit.DAYS),
                Instant.now().plus(15, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));

        UpdateAppointmentRequest updateBody = UpdateAppointmentRequest.builder()
                .notes("Cross tenant")
                .build();

        mockMvc.perform(patch("/appointments/{id}", appointmentId)
                        .with(crmUser(tenantA.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAppointment_adjacentSlot_succeeds() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant firstStart = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant firstEnd = firstStart.plus(2, ChronoUnit.HOURS);
        createAppointment(bundle, firstStart, firstEnd);

        Instant secondStart = firstEnd;
        Instant secondEnd = secondStart.plus(1, ChronoUnit.HOURS);
        CreateAppointmentRequest body = CreateAppointmentRequest.builder()
                .clientId(bundle.client().getId())
                .artistId(bundle.owner().getId())
                .serviceId(bundle.service().getId())
                .locationId(bundle.location().getId())
                .startTime(secondStart)
                .endTime(secondEnd)
                .price(BigDecimal.valueOf(1000))
                .build();

        mockMvc.perform(post("/appointments")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void updateAppointment_itemsExtendIntoConflict_returnsConflict() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Instant base = Instant.now().plus(11, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        UUID firstAppointmentId = createAppointment(bundle, base, base.plus(1, ChronoUnit.HOURS));
        createAppointment(bundle, base.plus(1, ChronoUnit.HOURS), base.plus(2, ChronoUnit.HOURS));

        UpdateAppointmentRequest updateBody = UpdateAppointmentRequest.builder()
                .items(List.of(AppointmentItemRequest.builder()
                        .source("custom")
                        .title("Extended session")
                        .unitPrice(BigDecimal.valueOf(1500))
                        .durationMinutes(120)
                        .quantity(1)
                        .sortOrder(0)
                        .build()))
                .adjustEndTimeFromItems(true)
                .build();

        mockMvc.perform(patch("/appointments/{id}", firstAppointmentId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.error.code").value("TIME_SLOT_CONFLICT"));
    }

    private UUID createAppointmentForArtist(TenantBundle bundle, Staff artist) throws Exception {
        Instant start = Instant.now().plus(20, ChronoUnit.DAYS);
        CreateAppointmentRequest body = CreateAppointmentRequest.builder()
                .clientId(bundle.client().getId())
                .artistId(artist.getId())
                .serviceId(bundle.service().getId())
                .locationId(bundle.location().getId())
                .startTime(start)
                .endTime(start.plus(1, ChronoUnit.HOURS))
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

    private void ensureDefaultPermissions(TenantBundle bundle) {
        SecurityTestSupport.authenticate(bundle.owner());
        rolePermissionService.getGrantedPermissions(bundle.tenant().getId(), UserRole.ARTIST);
        rolePermissionService.getGrantedPermissions(bundle.tenant().getId(), UserRole.ADMIN);
        SecurityTestSupport.clearAuthentication();
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
