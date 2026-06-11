package com.inkflow.crm.module.email.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.EmailLogRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.email.dto.EmailSettingsDto;
import com.inkflow.crm.module.email.dto.EmailTemplateDto;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.service.ResendEmailClient;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
class EmailControllerIntegrationTest {

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
    private EmailLogRepository emailLogRepository;

    @MockBean
    private ResendEmailClient resendEmailClient;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getStats_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/emails/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStats_withOwnerAuth_returnsOk() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/emails/stats").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalToday").exists());
    }

    @Test
    void getLog_withOwnerAuth_returnsOk() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/emails/log").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void send_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        SendEmailRequest body = new SendEmailRequest(
                List.of(bundle.client().getId()),
                "Campaign",
                "Hello clients"
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void send_withBlankSubject_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        SendEmailRequest body = new SendEmailRequest(
                List.of(bundle.client().getId()),
                "   ",
                "Hello clients"
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_withOwnerAuth_persistsEmailLogAndCallsResend() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Client client = bundle.client();
        client.setEmail("mailing-client-" + UUID.randomUUID() + "@test.com");
        clientRepository.save(client);

        SendEmailRequest body = new SendEmailRequest(
                List.of(client.getId()),
                "Studio update",
                "We moved to a new location"
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sent").value(1))
                .andExpect(jsonPath("$.data.skipped").value(0));

        verify(resendEmailClient).send(
                eq(client.getEmail()),
                eq("Studio update"),
                anyString()
        );

        var logs = emailLogRepository.findByTenantIdOrderBySentAtDesc(
                bundle.tenant().getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)
        ).getContent();
        assertEquals(1, logs.size());
        assertEquals(client.getEmail(), logs.get(0).getRecipientEmail());
        assertEquals(EmailType.MANUAL, logs.get(0).getType());
        assertEquals(EmailStatus.SENT, logs.get(0).getStatus());
    }

    @Test
    void getTemplates_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/emails/templates").with(crmUser(artist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateTemplate_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        EmailTemplateDto template = EmailTemplateDto.builder()
                .subject("Custom subject")
                .body("Hello")
                .build();

        mockMvc.perform(put("/emails/templates/confirmation")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(template)))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetTemplate_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(delete("/emails/templates/confirmation").with(crmUser(artist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getEmailSettings_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/emails/settings").with(crmUser(artist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchEmailSettings_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        EmailSettingsDto body = EmailSettingsDto.builder()
                .emailReminders(false)
                .reminderHoursBefore(48)
                .build();

        mockMvc.perform(patch("/emails/settings")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void send_withBlankBody_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        SendEmailRequest body = new SendEmailRequest(
                List.of(bundle.client().getId()),
                "Subject",
                "   "
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_withEmptyClientIds_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        SendEmailRequest body = new SendEmailRequest(
                Collections.emptyList(),
                "Subject",
                "Body"
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getLog_afterSend_returnsPersistedEmailLog() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Client client = bundle.client();
        client.setEmail("log-client-" + UUID.randomUUID() + "@test.com");
        clientRepository.save(client);

        SendEmailRequest sendBody = new SendEmailRequest(
                List.of(client.getId()),
                "Studio update",
                "We moved to a new location"
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendBody)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/emails/log").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].recipientEmail").value(client.getEmail()))
                .andExpect(jsonPath("$.data[0].subject").value("Studio update"))
                .andExpect(jsonPath("$.data[0].type").value("MANUAL"))
                .andExpect(jsonPath("$.data[0].status").value("SENT"));

        assertEquals(1, emailLogRepository.findByTenantIdOrderBySentAtDesc(
                bundle.tenant().getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void send_withClientFromOtherTenant_doesNotPersistLog() throws Exception {
        TenantBundle tenantA = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        TenantBundle tenantB = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Client otherTenantClient = tenantB.client();
        otherTenantClient.setEmail("other-tenant-" + UUID.randomUUID() + "@test.com");
        clientRepository.save(otherTenantClient);

        SendEmailRequest body = new SendEmailRequest(
                List.of(otherTenantClient.getId()),
                "Subject",
                "Body"
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(tenantA.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sent").value(0))
                .andExpect(jsonPath("$.data.skipped").value(0));

        verify(resendEmailClient, never()).send(anyString(), anyString(), anyString());
        assertEquals(0, emailLogRepository.findByTenantIdOrderBySentAtDesc(
                tenantA.tenant().getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void send_withClientWithoutEmail_skipsWithoutPersistingLog() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        SendEmailRequest body = new SendEmailRequest(
                List.of(bundle.client().getId()),
                "Subject",
                "Body"
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sent").value(0))
                .andExpect(jsonPath("$.data.skipped").value(1));

        verify(resendEmailClient, never()).send(anyString(), anyString(), anyString());
        assertEquals(0, emailLogRepository.findByTenantIdOrderBySentAtDesc(
                bundle.tenant().getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void getLog_withArtistAuth_afterPermissionsSeeded_returnsOk() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        var artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/settings/roles").with(crmUser(bundle.owner())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/emails/log").with(crmUser(artist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
