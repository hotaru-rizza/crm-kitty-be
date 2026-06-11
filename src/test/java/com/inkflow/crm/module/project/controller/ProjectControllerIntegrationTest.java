package com.inkflow.crm.module.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
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
                .status("on_hold")
                .title("Back Piece (Paused)")
                .build();

        mockMvc.perform(patch("/projects/{id}", projectId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("on_hold"))
                .andExpect(jsonPath("$.data.title").value("Back Piece (Paused)"));

        Project persisted = projectRepository.findById(projectId).orElseThrow();
        assertEquals(ProjectStatus.ON_HOLD, persisted.getStatus());
        assertEquals("Back Piece (Paused)", persisted.getTitle());
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
                        .content("{\"status\":\"archived\"}"))
                .andExpect(status().isBadRequest());
    }
}
