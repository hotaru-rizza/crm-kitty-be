package com.inkflow.crm.module.client.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.client.dto.CreateClientRequest;
import com.inkflow.crm.module.client.dto.UpdateClientRequest;
import com.inkflow.crm.module.settings.service.RolePermissionService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ClientControllerIntegrationTest {

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
    private RolePermissionService rolePermissionService;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getAllClients_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/clients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createClient_withOwnerAuth_returnsCreated() throws Exception {
        TenantBundle bundle = seedTenant();

        CreateClientRequest body = CreateClientRequest.builder()
                .firstName("Api")
                .lastName("Client")
                .email("api.client@test.com")
                .phone("+380501234567")
                .build();

        String createResponse = mockMvc.perform(post("/clients")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phone").value("+380501234567"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID clientId = UUID.fromString(
                objectMapper.readTree(createResponse).path("data").path("id").asText());

        Client persisted = clientRepository
                .findByIdAndDeletedAtIsNull(clientId)
                .orElseThrow();
        assertEquals("+380501234567", persisted.getPhone());
        assertEquals(false, persisted.isDormant());
        assertEquals(false, persisted.isBlacklisted());
    }

    @Test
    void createClient_withInvalidBody_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();

        CreateClientRequest body = CreateClientRequest.builder()
                .firstName("")
                .lastName("Client")
                .email("invalid-email")
                .build();

        mockMvc.perform(post("/clients")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getClient_fromOtherTenant_returnsNotFound() throws Exception {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        mockMvc.perform(get("/clients/{id}", tenantB.client().getId())
                        .with(crmUser(tenantA.owner())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.error.code").value("CLIENT_NOT_FOUND"));
    }

    @Test
    void getClient_withOwnerAuth_returnsClient() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/clients/{id}", bundle.client().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(bundle.client().getId().toString()));
    }

    @Test
    void updateClient_withOwnerAuth_persistsInDb() throws Exception {
        TenantBundle bundle = seedTenant();

        UpdateClientRequest body = UpdateClientRequest.builder()
                .firstName("Updated")
                .lastName("Name")
                .blacklisted(true)
                .build();

        mockMvc.perform(patch("/clients/{id}", bundle.client().getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Updated"))
                .andExpect(jsonPath("$.data.blacklisted").value(true));

        Client persisted = clientRepository
                .findByIdAndDeletedAtIsNull(bundle.client().getId())
                .orElseThrow();
        assertEquals("Updated", persisted.getFirstName());
        assertEquals("Name", persisted.getLastName());
        assertEquals(true, persisted.isBlacklisted());
    }

    @Test
    void updateClient_withInvalidBody_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();

        UpdateClientRequest body = UpdateClientRequest.builder()
                .phone("bad-phone")
                .build();

        mockMvc.perform(patch("/clients/{id}", bundle.client().getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteClient_withOwnerAuth_softDeletesInDb() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(delete("/clients/{id}", bundle.client().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Client deleted = clientRepository.findById(bundle.client().getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void updateClient_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateClientRequest body = UpdateClientRequest.builder()
                .firstName("Blocked")
                .build();

        mockMvc.perform(patch("/clients/{id}", bundle.client().getId())
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void deleteClient_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(delete("/clients/{id}", bundle.client().getId())
                        .with(crmUser(artist)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void getClient_artistCanViewClientTheyWorkedWith() throws Exception {
        TenantBundle bundle = seedTenant();
        ensureDefaultPermissions(bundle);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        linkArtistToClient(bundle, artist);

        mockMvc.perform(get("/clients/{id}", bundle.client().getId()).with(crmUser(artist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(bundle.client().getId().toString()));
    }

    @Test
    void getClient_artistCannotViewClientNeverWorkedWith() throws Exception {
        TenantBundle bundle = seedTenant();
        ensureDefaultPermissions(bundle);
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/clients/{id}", bundle.client().getId()).with(crmUser(artist)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void getClient_adminCanViewAnyClient() throws Exception {
        TenantBundle bundle = seedTenant();
        ensureDefaultPermissions(bundle);
        Staff admin = staffRepository.save(Staff.builder()
                .tenantId(bundle.tenant().getId())
                .firstName("Admin")
                .lastName("User")
                .email("admin-" + UUID.randomUUID() + "@test.com")
                .role(UserRole.ADMIN)
                .calendarColor("#6366f1")
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/clients/{id}", bundle.client().getId()).with(crmUser(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(bundle.client().getId().toString()));
    }

    private void linkArtistToClient(TenantBundle bundle, Staff artist) {
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
        appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(artist)
                .service(bundle.service())
                .location(bundle.location())
                .startTime(start)
                .endTime(start.plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());
    }

    private void ensureDefaultPermissions(TenantBundle bundle) {
        SecurityTestSupport.authenticate(bundle.owner());
        rolePermissionService.getGrantedPermissions(bundle.tenant().getId(), UserRole.ARTIST);
        rolePermissionService.getGrantedPermissions(bundle.tenant().getId(), UserRole.ADMIN);
        SecurityTestSupport.clearAuthentication();
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
