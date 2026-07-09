package com.inkflow.crm.module.email.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.email.dto.CreateEmailTemplateRequest;
import com.inkflow.crm.module.email.dto.EmailComposeRequest;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.UpdateEmailTemplateRequest;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.service.sending.ResendEmailClient;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private EmailMessageRepository emailMessageRepository;

    @MockBean
    private ResendEmailClient resendEmailClient;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getMessages_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/emails/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMessages_withOwnerAuth_returnsOk() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/emails/messages").with(crmUser(bundle.owner())))
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
                null,
                "Campaign",
                "Hello clients",
                false,
                null
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void send_withOwnerAuth_queuesPendingMessage() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Client client = bundle.client();
        client.setEmail("mailing-client-" + UUID.randomUUID() + "@test.com");
        clientRepository.save(client);

        SendEmailRequest body = new SendEmailRequest(
                List.of(client.getId()),
                null,
                "Studio update",
                "We moved to a new location",
                false,
                null
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sent").value(1))
                .andExpect(jsonPath("$.data.skipped").value(0));

        verify(resendEmailClient, never()).send(anyString(), anyString(), anyString());

        var messages = emailMessageRepository.findFiltered(
                TriggerType.MANUAL, null, null, null, PageRequest.of(0, 10)).getContent();
        assertEquals(1, messages.size());
        assertEquals(client.getEmail(), messages.get(0).getRecipientEmail());
        assertEquals(TriggerType.MANUAL, messages.get(0).getTriggerType());
        assertEquals(EmailMessageStatus.PENDING, messages.get(0).getStatus());
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
    void createTemplate_withOwnerAuth_persistsCustomTemplate() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        CreateEmailTemplateRequest template = new CreateEmailTemplateRequest(
                TriggerType.MANUAL, null, "Custom subject", "Hello {client_name}", true);

        mockMvc.perform(post("/emails/templates")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(template)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.data.deletable").value(true));
    }

    @Test
    void updateTemplate_withOwnerAuth_updatesExistingTemplate() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        CreateEmailTemplateRequest create = new CreateEmailTemplateRequest(
                TriggerType.MANUAL, null, "Old", "Old body", true);

        String createResponse = mockMvc.perform(post("/emails/templates")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UUID templateId = UUID.fromString(
                objectMapper.readTree(createResponse).path("data").path("id").asText());

        UpdateEmailTemplateRequest update = new UpdateEmailTemplateRequest(null, null, "New subject", null, true);

        mockMvc.perform(put("/emails/templates/" + templateId)
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("New subject"));
    }

    @Test
    void send_withNoRecipients_returnsBadRequest() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        SendEmailRequest body = new SendEmailRequest(
                Collections.emptyList(),
                null,
                "Subject",
                "Body",
                false,
                null
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_withClientFromOtherTenant_doesNotQueueMessage() throws Exception {
        TenantBundle tenantA = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        TenantBundle tenantB = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        Client otherTenantClient = tenantB.client();
        otherTenantClient.setEmail("other-tenant-" + UUID.randomUUID() + "@test.com");
        clientRepository.save(otherTenantClient);

        SendEmailRequest body = new SendEmailRequest(
                List.of(otherTenantClient.getId()),
                null,
                "Subject",
                "Body",
                false,
                null
        );

        mockMvc.perform(post("/emails/send")
                        .with(crmUser(tenantA.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sent").value(0))
                .andExpect(jsonPath("$.data.skipped").value(1));

        assertEquals(0, emailMessageRepository.findFiltered(
                null, null, null, null, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void deleteBuiltinTemplate_returnsForbidden() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/emails/templates").with(crmUser(bundle.owner())))
                .andExpect(status().isOk());

        String listResponse = mockMvc.perform(get("/emails/templates").with(crmUser(bundle.owner())))
                .andReturn().getResponse().getContentAsString();

        var templates = objectMapper.readTree(listResponse).path("data");
        UUID builtinId = null;
        for (var node : templates) {
            if (node.path("deletable").asBoolean(false) == false) {
                builtinId = UUID.fromString(node.path("id").asText());
                break;
            }
        }

        if (builtinId != null) {
            mockMvc.perform(delete("/emails/templates/" + builtinId).with(crmUser(bundle.owner())))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void preview_withOwnerAuth_returnsRenderedHtmlWithoutSending() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        EmailComposeRequest body = new EmailComposeRequest(
                "Flash Day",
                "<p>Hello {client_name} from {studio_name}</p>",
                true,
                null);

        mockMvc.perform(post("/emails/preview")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Олена")));

        verify(resendEmailClient, never()).send(anyString(), anyString(), anyString());
        assertEquals(0, emailMessageRepository.findFiltered(
                TriggerType.MANUAL, null, null, null, PageRequest.of(0, 10)).getTotalElements());
    }
}
