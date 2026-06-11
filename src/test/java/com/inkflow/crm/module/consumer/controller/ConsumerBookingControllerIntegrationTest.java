package com.inkflow.crm.module.consumer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingRequest;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
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

import java.util.List;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.consumerUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ConsumerBookingControllerIntegrationTest {

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
    private ConsumerUserRepository consumerUserRepository;

    @Autowired
    private RequestRepository requestRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void submitBooking_withoutAuth_returnsUnauthorized() throws Exception {
        ConsumerBookingRequest body = sampleBookingRequest(UUID.randomUUID());

        mockMvc.perform(post("/public/consumer/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void submitBooking_withConsumerAuth_returnsCreated() throws Exception {
        UUID consumerId = seedConsumer().getId();
        Staff artist = seedPublicArtist();

        ConsumerBookingRequest body = sampleBookingRequest(artist.getId());

        String response = mockMvc.perform(post("/public/consumer/requests")
                        .with(consumerUser(consumerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("new"))
                .andExpect(jsonPath("$.data.artistName").value("Alex Ink"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID requestId = UUID.fromString(
                objectMapper.readTree(response).path("data").path("id").asText());

        Request persisted = requestRepository.findById(requestId).orElseThrow();
        assertEquals(consumerId, persisted.getConsumerUserId());
        assertEquals(RequestSource.APP, persisted.getSource());
        assertEquals(RequestStatus.NEW, persisted.getStatus());
        assertEquals("Maria", persisted.getClientName());
        assertEquals("Rose tattoo", persisted.getIdea());
        assertEquals(artist.getTenantId(), persisted.getTenantId());
        assertEquals(artist.getId(), persisted.getAssignedStaff().getId());
    }

    @Test
    void submitBooking_withEmptyBody_returnsBadRequest() throws Exception {
        UUID consumerId = seedConsumer().getId();

        mockMvc.perform(post("/public/consumer/requests")
                        .with(consumerUser(consumerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void submitBooking_withUnknownArtist_returnsNotFound() throws Exception {
        UUID consumerId = seedConsumer().getId();
        ConsumerBookingRequest body = sampleBookingRequest(UUID.randomUUID());

        mockMvc.perform(post("/public/consumer/requests")
                        .with(consumerUser(consumerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.error.code").value("STAFF_NOT_FOUND"));
    }

    @Test
    void getMyRequests_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/public/consumer/requests/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void getMyRequests_withConsumerAuth_returnsSubmittedRequests() throws Exception {
        UUID consumerId = seedConsumer().getId();
        Staff artist = seedPublicArtist();
        ConsumerBookingRequest body = sampleBookingRequest(artist.getId());

        mockMvc.perform(post("/public/consumer/requests")
                        .with(consumerUser(consumerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/public/consumer/requests/my")
                        .with(consumerUser(consumerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].artistName").value("Alex Ink"))
                .andExpect(jsonPath("$.data[0].idea").value("Rose tattoo"));
    }

    private ConsumerUser seedConsumer() {
        UUID id = UUID.randomUUID();
        return consumerUserRepository.save(new ConsumerUser(id, "consumer-" + id + "@test.com", "Maria"));
    }

    private Staff seedPublicArtist() {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        return staffRepository.save(Staff.builder()
                .tenantId(bundle.tenant().getId())
                .firstName("Alex")
                .lastName("Ink")
                .email("artist-" + UUID.randomUUID() + "@test.com")
                .role(UserRole.ARTIST)
                .calendarColor("#6366f1")
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .isPublic(true)
                .isServiceProvider(true)
                .build());
    }

    private ConsumerBookingRequest sampleBookingRequest(UUID artistId) {
        return new ConsumerBookingRequest(
                artistId,
                "Maria",
                "next month",
                "medium",
                List.of("arm"),
                false,
                "Rose tattoo",
                List.of("https://ref/1.jpg"),
                "Kyiv",
                "telegram",
                "@maria",
                "+380501112233",
                "@maria_ink"
        );
    }
}
