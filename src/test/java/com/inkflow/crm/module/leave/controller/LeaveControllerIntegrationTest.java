package com.inkflow.crm.module.leave.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.enums.LeaveStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LeaveRequestRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.leave.dto.CreateLeaveRequest;
import com.inkflow.crm.module.leave.dto.UpdateLeaveStatusRequest;
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

import java.time.LocalDate;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class LeaveControllerIntegrationTest {

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
    private LeaveRequestRepository leaveRequestRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    private void linkOwnerAuth(TenantBundle bundle) {
        bundle.owner().setAuthUserId(bundle.owner().getId().toString());
        staffRepository.save(bundle.owner());
    }

    @Test
    void getAllLeaves_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/leaves"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createLeave_withOwnerAuth_returnsOk() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        CreateLeaveRequest body = CreateLeaveRequest.builder()
                .staffId(bundle.owner().getId())
                .leaveType("VACATION")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 5))
                .reason("Summer break")
                .build();

        mockMvc.perform(post("/leaves")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.staffId").value(bundle.owner().getId().toString()));
    }

    @Test
    void getPendingCount_withOwnerAuth_returnsOk() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/leaves/pending-count").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    void getAllLeaves_withOwnerAuth_returnsCreatedLeave() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        linkOwnerAuth(bundle);

        CreateLeaveRequest body = CreateLeaveRequest.builder()
                .staffId(bundle.owner().getId())
                .leaveType("VACATION")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 5))
                .reason("Summer break")
                .build();

        mockMvc.perform(post("/leaves")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/leaves").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].staffId").value(bundle.owner().getId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("APPROVED"));
    }

    @Test
    void getLeaveById_withOwnerAuth_returnsLeave() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        String createResponse = mockMvc.perform(post("/leaves")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateLeaveRequest.builder()
                                .staffId(bundle.owner().getId())
                                .leaveType("SICK_LEAVE")
                                .startDate(LocalDate.of(2026, 8, 1))
                                .endDate(LocalDate.of(2026, 8, 2))
                                .build())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String leaveId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        mockMvc.perform(get("/leaves/{id}", leaveId).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(leaveId))
                .andExpect(jsonPath("$.data.leaveType").value("SICK_LEAVE"));
    }

    @Test
    void updateLeaveStatus_withOwnerAuth_rejectsApprovedLeave() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        String createResponse = mockMvc.perform(post("/leaves")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateLeaveRequest.builder()
                                .staffId(bundle.owner().getId())
                                .leaveType("VACATION")
                                .startDate(LocalDate.of(2026, 9, 1))
                                .endDate(LocalDate.of(2026, 9, 3))
                                .build())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String leaveId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        UpdateLeaveStatusRequest statusUpdate = new UpdateLeaveStatusRequest();
        statusUpdate.setStatus("REJECTED");

        mockMvc.perform(patch("/leaves/{id}/status", leaveId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        LeaveRequest persisted = leaveRequestRepository.findById(UUID.fromString(leaveId)).orElseThrow();
        assertEquals(LeaveStatus.REJECTED, persisted.getStatus());
    }

    @Test
    void cancelLeave_withOwnerAuth_setsCancelledStatus() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        String createResponse = mockMvc.perform(post("/leaves")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateLeaveRequest.builder()
                                .staffId(bundle.owner().getId())
                                .leaveType("PERSONAL")
                                .startDate(LocalDate.of(2026, 10, 1))
                                .endDate(LocalDate.of(2026, 10, 2))
                                .build())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String leaveId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        mockMvc.perform(patch("/leaves/{id}/cancel", leaveId).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        LeaveRequest persisted = leaveRequestRepository.findById(UUID.fromString(leaveId)).orElseThrow();
        assertEquals(LeaveStatus.CANCELLED, persisted.getStatus());
    }

    @Test
    void checkStaffOnLeave_withOwnerAuth_returnsTrueDuringLeave() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        linkOwnerAuth(bundle);

        mockMvc.perform(post("/leaves")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateLeaveRequest.builder()
                                .staffId(bundle.owner().getId())
                                .leaveType("VACATION")
                                .startDate(LocalDate.of(2026, 11, 1))
                                .endDate(LocalDate.of(2026, 11, 5))
                                .build())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/leaves/staff/{staffId}/check", bundle.owner().getId())
                        .param("date", "2026-11-03")
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void getLeavesForCalendar_withOwnerAuth_returnsApprovedLeaves() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        linkOwnerAuth(bundle);

        mockMvc.perform(post("/leaves")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateLeaveRequest.builder()
                                .staffId(bundle.owner().getId())
                                .leaveType("VACATION")
                                .startDate(LocalDate.of(2026, 12, 1))
                                .endDate(LocalDate.of(2026, 12, 3))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/leaves/calendar")
                        .param("from", "2026-12-01")
                        .param("to", "2026-12-31")
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].staffId").value(bundle.owner().getId().toString()));
    }

    @Test
    void deleteLeave_withOwnerAuth_returnsOk() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        String createResponse = mockMvc.perform(post("/leaves")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateLeaveRequest.builder()
                                .staffId(bundle.owner().getId())
                                .leaveType("VACATION")
                                .startDate(LocalDate.of(2027, 1, 1))
                                .endDate(LocalDate.of(2027, 1, 2))
                                .build())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String leaveId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        mockMvc.perform(delete("/leaves/{id}", leaveId).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/leaves/{id}", leaveId).with(crmUser(bundle.owner())))
                .andExpect(status().isNotFound());
    }
}
