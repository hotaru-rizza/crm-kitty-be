package com.inkflow.crm.module.location.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.location.dto.AssignStaffRequest;
import com.inkflow.crm.module.location.dto.CreateLocationRequest;
import com.inkflow.crm.module.location.dto.UpdateLocationRequest;

import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class LocationControllerIntegrationTest {

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
    private RolePermissionService rolePermissionService;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getAllLocations_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/locations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createLocation_withOwnerAuth_persistsInDb() throws Exception {
        TenantBundle bundle = seedTenant();

        CreateLocationRequest body = CreateLocationRequest.builder()
                .name("Second Studio")
                .address("Kyiv, Main St 1")
                .color("#6366f1")
                .build();

        String response = mockMvc.perform(post("/locations")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Second Studio"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String locationId = objectMapper.readTree(response).path("data").path("id").asText();
        Location saved = locationRepository.findById(java.util.UUID.fromString(locationId)).orElseThrow();
        assertEquals(bundle.tenant().getId(), saved.getTenantId());
        assertEquals("Second Studio", saved.getName());
        assertEquals("Kyiv, Main St 1", saved.getAddress());
        assertTrue(saved.getIsActive());
    }

    @Test
    void updateLocation_withOwnerAuth_updatesDb() throws Exception {
        TenantBundle bundle = seedTenant();

        UpdateLocationRequest body = UpdateLocationRequest.builder()
                .name("Renamed Studio")
                .address("Lviv, New St 5")
                .build();

        mockMvc.perform(patch("/locations/{id}", bundle.location().getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed Studio"));

        Location updated = locationRepository.findById(bundle.location().getId()).orElseThrow();
        assertEquals("Renamed Studio", updated.getName());
        assertEquals("Lviv, New St 5", updated.getAddress());
    }

    @Test
    void deleteLocation_withOwnerAuth_softDeletesInDb() throws Exception {
        TenantBundle bundle = seedTenant();

        CreateLocationRequest extraLocation = CreateLocationRequest.builder()
                .name("Backup Studio")
                .address("Kyiv, Backup St 2")
                .color("#14b8a6")
                .build();

        mockMvc.perform(post("/locations")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(extraLocation)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/locations/{id}", bundle.location().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Location deleted = locationRepository.findById(bundle.location().getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void deleteLocation_withAdminAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff admin = seedAdmin(bundle);
        ensureDefaultPermissions(bundle);

        CreateLocationRequest extraLocation = CreateLocationRequest.builder()
                .name("Backup Studio")
                .address("Kyiv, Backup St 2")
                .color("#14b8a6")
                .build();

        mockMvc.perform(post("/locations")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(extraLocation)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/locations/{id}", bundle.location().getId())
                        .with(crmUser(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));

        assertTrue(locationRepository.findByIdAndDeletedAtIsNull(bundle.location().getId()).isPresent());
    }

    @Test
    void createLocation_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        CreateLocationRequest body = CreateLocationRequest.builder()
                .name("Second Studio")
                .address("Kyiv, Main St 1")
                .color("#6366f1")
                .build();

        mockMvc.perform(post("/locations")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void createLocation_withInvalidBody_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();

        CreateLocationRequest body = CreateLocationRequest.builder()
                .name("X")
                .address("")
                .color("invalid")
                .build();

        mockMvc.perform(post("/locations")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getLocation_withOwnerAuth_returnsDetail() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(get("/locations/{id}", bundle.location().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(bundle.location().getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Main Studio"));
    }

    @Test
    void assignStaff_withOwnerAuth_persistsInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        AssignStaffRequest body = AssignStaffRequest.builder()
                .staffIds(List.of(artist.getId()))
                .build();

        mockMvc.perform(post("/locations/{id}/staff", bundle.location().getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Location updated = locationRepository.findById(bundle.location().getId()).orElseThrow();
        assertEquals(1, updated.getStaff().size());
        assertTrue(updated.getStaff().stream()
                .anyMatch(staff -> staff.getId().equals(artist.getId())));
    }

    @Test
    void assignStaff_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        AssignStaffRequest body = AssignStaffRequest.builder()
                .staffIds(List.of(artist.getId()))
                .build();

        mockMvc.perform(post("/locations/{id}/staff", bundle.location().getId())
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void assignStaff_withEmptyStaffIds_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();

        AssignStaffRequest body = AssignStaffRequest.builder()
                .staffIds(List.of())
                .build();

        mockMvc.perform(post("/locations/{id}/staff", bundle.location().getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getLocation_fromOtherTenant_returnsNotFound() throws Exception {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        mockMvc.perform(get("/locations/{id}", tenantB.location().getId())
                        .with(crmUser(tenantA.owner())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.error.code").value("LOCATION_NOT_FOUND"));
    }

    private TenantBundle seedTenant() {
        return IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
    }

    private Staff seedAdmin(TenantBundle bundle) {
        return staffRepository.save(Staff.builder()
                .tenantId(bundle.tenant().getId())
                .firstName("Admin")
                .lastName("User")
                .email("admin-" + UUID.randomUUID() + "@test.com")
                .role(UserRole.ADMIN)
                .calendarColor("#6366f1")
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private void ensureDefaultPermissions(TenantBundle bundle) {
        SecurityTestSupport.authenticate(bundle.owner());
        rolePermissionService.getGrantedPermissions(bundle.tenant().getId(), UserRole.ADMIN);
        SecurityTestSupport.clearAuthentication();
    }
}
