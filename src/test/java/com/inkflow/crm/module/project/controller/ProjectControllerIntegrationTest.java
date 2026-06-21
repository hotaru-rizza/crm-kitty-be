package com.inkflow.crm.module.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.GalleryStage;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.GalleryPhotoRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.project.dto.CreateProjectRequest;
import com.inkflow.crm.module.project.dto.UpdateProjectRequest;
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
import java.util.UUID;

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
class ProjectControllerIntegrationTest {

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
    private ProjectRepository projectRepository;

    @Autowired
    private GalleryPhotoRepository galleryPhotoRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getAllProjects_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProject_withOwnerAuth_returnsCreated() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        CreateProjectRequest body = CreateProjectRequest.builder()
                .title("Sleeve Project")
                .clientId(bundle.client().getId())
                .artistId(bundle.owner().getId())
                .locationId(bundle.location().getId())
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(4)
                .build();

        mockMvc.perform(post("/projects")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Sleeve Project"))
                .andExpect(jsonPath("$.data.status").value("in_progress"));

        long count = projectRepository.findAll().stream()
                .filter(p -> bundle.tenant().getId().equals(p.getTenantId()))
                .count();
        assertEquals(1, count);

        Project persisted = projectRepository.findAll().stream()
                .filter(p -> bundle.tenant().getId().equals(p.getTenantId()))
                .findFirst()
                .orElseThrow();
        assertEquals(ProjectStatus.IN_PROGRESS, persisted.getStatus());
        assertEquals(BigDecimal.valueOf(5000), persisted.getEstimatedCost());
        assertEquals(bundle.client().getId(), persisted.getClient().getId());
    }

    @Test
    void createProject_withEmptyBody_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(post("/projects")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProject_changesStatusAndPersistsInDb() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        CreateProjectRequest createBody = CreateProjectRequest.builder()
                .title("Back Piece")
                .clientId(bundle.client().getId())
                .artistId(bundle.owner().getId())
                .locationId(bundle.location().getId())
                .estimatedCost(BigDecimal.valueOf(8000))
                .totalSessions(5)
                .build();

        String createResponse = mockMvc.perform(post("/projects")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID projectId = UUID.fromString(
                objectMapper.readTree(createResponse).path("data").path("id").asText());

        UpdateProjectRequest updateBody = UpdateProjectRequest.builder()
                .status("archived")
                .title("Back Piece (Archived)")
                .build();

        mockMvc.perform(patch("/projects/{id}", projectId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("archived"))
                .andExpect(jsonPath("$.data.title").value("Back Piece (Archived)"));

        Project persisted = projectRepository.findById(projectId).orElseThrow();
        assertEquals(ProjectStatus.ARCHIVED, persisted.getStatus());
        assertEquals("Back Piece (Archived)", persisted.getTitle());
    }

    @Test
    void getProject_withOwnerAuth_returnsProject() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID projectId = createProjectAndGetId(bundle);

        mockMvc.perform(get("/projects/{id}", projectId).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(projectId.toString()))
                .andExpect(jsonPath("$.data.title").value("Sleeve Project"));
    }

    @Test
    void deleteProject_withOwnerAuth_softDeletesInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID projectId = createProjectAndGetId(bundle);

        mockMvc.perform(patch("/projects/{id}", projectId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"archived\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/projects/{id}", projectId).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Project deleted = projectRepository.findById(projectId).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void deleteProject_whenNotArchived_returnsUnprocessableEntity() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID projectId = createProjectAndGetId(bundle);

        mockMvc.perform(delete("/projects/{id}", projectId).with(crmUser(bundle.owner())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createProject_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        CreateProjectRequest body = CreateProjectRequest.builder()
                .title("Blocked Project")
                .clientId(bundle.client().getId())
                .artistId(artist.getId())
                .estimatedCost(BigDecimal.valueOf(1000))
                .totalSessions(1)
                .build();

        mockMvc.perform(post("/projects")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void deleteProject_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        UUID projectId = createProjectAndGetId(bundle);

        mockMvc.perform(delete("/projects/{id}", projectId).with(crmUser(artist)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));
    }

    @Test
    void getProject_fromOtherTenant_returnsNotFound() throws Exception {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        UUID projectId = createProjectAndGetId(tenantB);

        mockMvc.perform(get("/projects/{id}", projectId).with(crmUser(tenantA.owner())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void addProjectPhoto_persistsInGalleryPhotoRepository() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID projectId = createProjectAndGetId(bundle);

        String response = mockMvc.perform(post("/projects/{id}/photos", projectId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://cdn.example.com/healed.jpg\",\"stage\":\"healed\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/healed.jpg"))
                .andExpect(jsonPath("$.data.stage").value("healed"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID photoId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());

        var photos = galleryPhotoRepository.findByProjectId(projectId);
        assertEquals(1, photos.size());
        assertEquals(photoId, photos.get(0).getId());
        assertEquals(GalleryStage.HEALED, photos.get(0).getStage());
        assertEquals(bundle.tenant().getId(), photos.get(0).getTenantId());
    }

    @Test
    void deleteProjectPhoto_removesFromDb() throws Exception {
        TenantBundle bundle = seedTenant();
        UUID projectId = createProjectAndGetId(bundle);

        String createResponse = mockMvc.perform(post("/projects/{id}/photos", projectId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://cdn.example.com/sketch.jpg\",\"stage\":\"sketch\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID photoId = UUID.fromString(
                objectMapper.readTree(createResponse).path("data").path("id").asText());

        mockMvc.perform(delete("/projects/{id}/photos/{photoId}", projectId, photoId)
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertEquals(0, galleryPhotoRepository.findByProjectId(projectId).size());
    }

    @Test
    void updateProject_withInvalidStatus_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        CreateProjectRequest createBody = CreateProjectRequest.builder()
                .title("Forearm")
                .clientId(bundle.client().getId())
                .artistId(bundle.owner().getId())
                .estimatedCost(BigDecimal.valueOf(2000))
                .totalSessions(2)
                .build();

        String createResponse = mockMvc.perform(post("/projects")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID projectId = UUID.fromString(
                objectMapper.readTree(createResponse).path("data").path("id").asText());

        mockMvc.perform(patch("/projects/{id}", projectId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"on_hold\"}"))
                .andExpect(status().isBadRequest());
    }

    private UUID createProjectAndGetId(TenantBundle bundle) throws Exception {
        CreateProjectRequest body = CreateProjectRequest.builder()
                .title("Sleeve Project")
                .clientId(bundle.client().getId())
                .artistId(bundle.owner().getId())
                .locationId(bundle.location().getId())
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(4)
                .build();

        String response = mockMvc.perform(post("/projects")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    private TenantBundle seedTenant() {
        return IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
    }
}
