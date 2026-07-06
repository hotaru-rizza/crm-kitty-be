package com.inkflow.crm.module.staff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.ArtistServicePricing;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffFaq;
import com.inkflow.crm.domain.entity.StaffInvite;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.DayOfWeek;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ArtistServicePricingRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffFaqRepository;
import com.inkflow.crm.domain.repository.StaffInviteRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.StaffScheduleRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.staff.dto.AcceptInviteRequest;
import com.inkflow.crm.module.staff.dto.AddStaffServiceRequest;
import com.inkflow.crm.module.staff.dto.CreateStaffRequest;
import com.inkflow.crm.module.staff.dto.InviteStaffRequest;
import com.inkflow.crm.module.staff.dto.UpdateScheduleRequest;
import com.inkflow.crm.module.staff.dto.UpdateStaffRequest;
import com.inkflow.crm.module.staff.dto.UpdateStaffServicesRequest;
import com.inkflow.crm.module.staff.dto.UpsertFaqRequest;
import com.inkflow.crm.module.settings.service.RolePermissionService;
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
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class StaffControllerIntegrationTest {

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
    private StaffScheduleRepository staffScheduleRepository;

    @Autowired
    private StaffFaqRepository staffFaqRepository;

    @Autowired
    private ArtistServicePricingRepository artistServicePricingRepository;

    @Autowired
    private StaffInviteRepository staffInviteRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private RolePermissionService rolePermissionService;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getAllStaff_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/staff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllStaff_withOwnerAuth_returnsOk() throws Exception {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);

        mockMvc.perform(get("/staff").with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getStaffDetail_withOwnerAuth_returnsFieldsMatchingDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff owner = staffRepository.findById(bundle.owner().getId()).orElseThrow();

        mockMvc.perform(get("/staff/{id}", owner.getId()).with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.data.firstName").value(owner.getFirstName()))
                .andExpect(jsonPath("$.data.lastName").value(owner.getLastName()))
                .andExpect(jsonPath("$.data.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.data.role").value(owner.getRole().getValue()));
    }

    @Test
    void getAllStaff_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/staff").with(crmUser(artist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createStaff_withMissingRequiredFields_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();

        mockMvc.perform(post("/staff")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createStaff_withOwnerAuth_persistsStaffInDb() throws Exception {
        TenantBundle bundle = seedTenant();

        CreateStaffRequest body = CreateStaffRequest.builder()
                .firstName("New")
                .lastName("Member")
                .email("new-member@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(List.of(bundle.location().getId()))
                .build();

        mockMvc.perform(post("/staff")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new-member@test.com"));

        assertTrue(staffRepository.existsByEmailAndDeletedAtIsNull("new-member@test.com"));
    }

    @Test
    void createStaff_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        CreateStaffRequest body = CreateStaffRequest.builder()
                .firstName("Blocked")
                .lastName("Member")
                .email("blocked@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(List.of(bundle.location().getId()))
                .build();

        mockMvc.perform(post("/staff")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStaff_withOwnerAuth_persistsChangesInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateStaffRequest body = UpdateStaffRequest.builder()
                .firstName("Renamed")
                .lastName("Artist")
                .bio("Specializes in blackwork")
                .build();

        mockMvc.perform(patch("/staff/{id}", artist.getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Renamed"))
                .andExpect(jsonPath("$.data.bio").value("Specializes in blackwork"));

        Staff updated = staffRepository.findById(artist.getId()).orElseThrow();
        assertEquals("Renamed", updated.getFirstName());
        assertEquals("Artist", updated.getLastName());
        assertEquals("Specializes in blackwork", updated.getBio());
    }

    @Test
    void updateStaff_withInvalidEmail_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateStaffRequest body = UpdateStaffRequest.builder()
                .email("not-an-email")
                .build();

        mockMvc.perform(patch("/staff/{id}", artist.getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStaff_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateStaffRequest body = UpdateStaffRequest.builder()
                .firstName("Blocked")
                .build();

        mockMvc.perform(patch("/staff/{id}", artist.getId())
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteStaff_withOwnerAuth_softDeletesInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(delete("/staff/{id}", artist.getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(staffRepository.findByIdAndDeletedAtIsNull(artist.getId()).isEmpty());
        Staff deleted = staffRepository.findById(artist.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    @Test
    void deleteStaff_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        Staff target = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(delete("/staff/{id}", target.getId())
                        .with(crmUser(artist)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteStaff_withAdminAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff admin = seedAdmin(bundle);
        Staff target = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        ensureDefaultPermissions(bundle);

        mockMvc.perform(delete("/staff/{id}", target.getId())
                        .with(crmUser(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.error.code").value("FORBIDDEN"));

        assertTrue(staffRepository.findByIdAndDeletedAtIsNull(target.getId()).isPresent());
    }

    @Test
    void updateSchedule_withOwnerAuth_persistsScheduleInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateScheduleRequest body = UpdateScheduleRequest.builder()
                .schedule(List.of(
                        UpdateScheduleRequest.ScheduleEntry.builder()
                                .dayOfWeek("monday")
                                .isWorking(true)
                                .startTime("10:00")
                                .endTime("18:00")
                                .build(),
                        UpdateScheduleRequest.ScheduleEntry.builder()
                                .dayOfWeek("sunday")
                                .isWorking(false)
                                .build()
                ))
                .build();

        mockMvc.perform(put("/staff/{id}/schedule", artist.getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        List<StaffSchedule> schedules = staffScheduleRepository.findByStaffId(artist.getId());
        assertEquals(2, schedules.size());

        StaffSchedule monday = schedules.stream()
                .filter(s -> s.getDayOfWeek() == DayOfWeek.MONDAY)
                .findFirst()
                .orElseThrow();
        assertEquals(LocalTime.of(10, 0), monday.getStartTime());
        assertEquals(LocalTime.of(18, 0), monday.getEndTime());
        assertTrue(monday.getIsWorking());

        StaffSchedule sunday = schedules.stream()
                .filter(s -> s.getDayOfWeek() == DayOfWeek.SUNDAY)
                .findFirst()
                .orElseThrow();
        assertFalse(sunday.getIsWorking());
    }

    @Test
    void updateSchedule_withEmptySchedule_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateScheduleRequest body = UpdateScheduleRequest.builder()
                .schedule(List.of())
                .build();

        mockMvc.perform(put("/staff/{id}/schedule", artist.getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSchedule_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateScheduleRequest body = UpdateScheduleRequest.builder()
                .schedule(List.of(
                        UpdateScheduleRequest.ScheduleEntry.builder()
                                .dayOfWeek("monday")
                                .isWorking(true)
                                .startTime("09:00")
                                .endTime("17:00")
                                .build()
                ))
                .build();

        mockMvc.perform(put("/staff/{id}/schedule", artist.getId())
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addServiceToStaff_withOwnerAuth_persistsPricingInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        AddStaffServiceRequest body = AddStaffServiceRequest.builder()
                .customPrice(BigDecimal.valueOf(1200))
                .customDuration(90)
                .build();

        mockMvc.perform(post("/staff/{id}/services/{serviceId}", artist.getId(), bundle.service().getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customPrice").value(1200))
                .andExpect(jsonPath("$.data.customDuration").value(90));

        ArtistServicePricing pricing = artistServicePricingRepository
                .findByStaffIdAndServiceId(artist.getId(), bundle.service().getId())
                .orElseThrow();
        assertEquals(BigDecimal.valueOf(1200), pricing.getPrice());
        assertEquals(90, pricing.getDuration());
    }

    @Test
    void updateStaffServicePricing_withOwnerAuth_updatesDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        artistServicePricingRepository.save(ArtistServicePricing.builder()
                .staff(artist)
                .service(bundle.service())
                .price(bundle.service().getPrice())
                .duration(bundle.service().getDuration())
                .build());

        AddStaffServiceRequest body = AddStaffServiceRequest.builder()
                .customPrice(BigDecimal.valueOf(1500))
                .customDuration(120)
                .build();

        mockMvc.perform(put("/staff/{id}/services/{serviceId}", artist.getId(), bundle.service().getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customPrice").value(1500))
                .andExpect(jsonPath("$.data.customDuration").value(120));

        ArtistServicePricing pricing = artistServicePricingRepository
                .findByStaffIdAndServiceId(artist.getId(), bundle.service().getId())
                .orElseThrow();
        assertEquals(BigDecimal.valueOf(1500), pricing.getPrice());
        assertEquals(120, pricing.getDuration());
    }

    @Test
    void updateStaffServices_withOwnerAuth_replacesAssignmentsInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateStaffServicesRequest body = UpdateStaffServicesRequest.builder()
                .services(List.of(
                        UpdateStaffServicesRequest.ServiceAssignment.builder()
                                .serviceId(bundle.service().getId())
                                .customPrice(BigDecimal.valueOf(1100))
                                .customDuration(75)
                                .build()
                ))
                .build();

        mockMvc.perform(put("/staff/{id}/services", artist.getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].customPrice").value(1100));

        List<ArtistServicePricing> assignments = artistServicePricingRepository.findByStaffId(artist.getId());
        assertEquals(1, assignments.size());
        assertEquals(BigDecimal.valueOf(1100), assignments.getFirst().getPrice());
        assertEquals(75, assignments.getFirst().getDuration());
    }

    @Test
    void removeServiceFromStaff_withOwnerAuth_removesFromDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        artistServicePricingRepository.save(ArtistServicePricing.builder()
                .staff(artist)
                .service(bundle.service())
                .price(bundle.service().getPrice())
                .duration(bundle.service().getDuration())
                .build());

        mockMvc.perform(delete("/staff/{id}/services/{serviceId}", artist.getId(), bundle.service().getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(artistServicePricingRepository
                .findByStaffIdAndServiceId(artist.getId(), bundle.service().getId())
                .isEmpty());
    }

    @Test
    void getStaffServices_withOwnerAuth_returnsAssignedServices() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        artistServicePricingRepository.save(ArtistServicePricing.builder()
                .staff(artist)
                .service(bundle.service())
                .price(BigDecimal.valueOf(999))
                .duration(45)
                .build());

        mockMvc.perform(get("/staff/{id}/services", artist.getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].serviceId").value(bundle.service().getId().toString()))
                .andExpect(jsonPath("$.data[0].customPrice").value(999));
    }

    @Test
    void addServiceToStaff_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(post("/staff/{id}/services/{serviceId}", artist.getId(), bundle.service().getId())
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStaffServices_withMissingServices_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(put("/staff/{id}/services", artist.getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFaq_withOwnerAuth_returnsEmptyWhenNone() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(get("/staff/{id}/faq", artist.getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void upsertFaq_withOwnerAuth_persistsItemsInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpsertFaqRequest body = UpsertFaqRequest.builder()
                .items(List.of(
                        new UpsertFaqRequest.FaqItem("How long?", "About 2 hours"),
                        new UpsertFaqRequest.FaqItem("Deposit?", "50% upfront")
                ))
                .build();

        mockMvc.perform(put("/staff/{id}/faq", artist.getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].question").value("How long?"))
                .andExpect(jsonPath("$.data[1].question").value("Deposit?"));

        List<StaffFaq> faq = staffFaqRepository.findByStaffIdOrderBySortOrderAsc(artist.getId());
        assertEquals(2, faq.size());
        assertEquals("How long?", faq.get(0).getQuestion());
        assertEquals("About 2 hours", faq.get(0).getAnswer());
        assertEquals("Deposit?", faq.get(1).getQuestion());
    }

    @Test
    void updateSchedule_withInvalidDayOfWeek_returnsBadRequest() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpdateScheduleRequest body = UpdateScheduleRequest.builder()
                .schedule(List.of(
                        UpdateScheduleRequest.ScheduleEntry.builder()
                                .dayOfWeek("funday")
                                .isWorking(true)
                                .startTime("09:00")
                                .endTime("17:00")
                                .build()
                ))
                .build();

        mockMvc.perform(put("/staff/{id}/schedule", artist.getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upsertFaq_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        UpsertFaqRequest body = UpsertFaqRequest.builder()
                .items(List.of(new UpsertFaqRequest.FaqItem("Q?", "A.")))
                .build();

        mockMvc.perform(put("/staff/{id}/faq", artist.getId())
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void inviteStaff_withoutAuth_returnsUnauthorized() throws Exception {
        TenantBundle bundle = seedTenant();

        InviteStaffRequest body = InviteStaffRequest.builder()
                .email("invite-target@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(List.of(bundle.location().getId()))
                .build();

        mockMvc.perform(post("/staff/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inviteStaff_withOwnerAuth_persistsStaffInviteInDb() throws Exception {
        TenantBundle bundle = seedTenant();

        InviteStaffRequest body = InviteStaffRequest.builder()
                .email("invite-target@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(List.of(bundle.location().getId()))
                .build();

        mockMvc.perform(post("/staff/invite")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        StaffInvite invite = staffInviteRepository
                .findByEmailAndAcceptedAtIsNull("invite-target@test.com")
                .orElseThrow();
        assertEquals("invite-target@test.com", invite.getEmail());
        assertEquals(UserRole.ARTIST, invite.getRole());
        assertEquals(bundle.tenant().getId(), invite.getTenantId());
        assertEquals(bundle.owner().getId(), invite.getInvitedBy());
        assertNull(invite.getAcceptedAt());
        assertFalse(invite.isExpired());
        assertTrue(invite.getLocationIds().contains(bundle.location().getId()));
    }

    @Test
    void getInviteInfo_withValidToken_returnsInviteDetails() throws Exception {
        TenantBundle bundle = seedTenant();
        String token = createInviteAndReturnToken(bundle, "invite-info@test.com");

        mockMvc.perform(get("/staff/invite/info/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("invite-info@test.com"))
                .andExpect(jsonPath("$.data.role").value("artist"))
                .andExpect(jsonPath("$.data.expired").value(false))
                .andExpect(jsonPath("$.data.accepted").value(false));
    }

    @Test
    void acceptInvite_withValidToken_createsStaffAndMarksInviteAccepted() throws Exception {
        TenantBundle bundle = seedTenant();
        String token = createInviteAndReturnToken(bundle, "accepted-artist@test.com");

        AcceptInviteRequest body = AcceptInviteRequest.builder()
                .token(token)
                .firstName("Invited")
                .lastName("Artist")
                .phone("+380991234567")
                .authUserId(UUID.randomUUID().toString())
                .build();

        mockMvc.perform(post("/staff/accept-invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("accepted-artist@test.com"))
                .andExpect(jsonPath("$.data.firstName").value("Invited"))
                .andExpect(jsonPath("$.data.lastName").value("Artist"));

        assertTrue(staffRepository.existsByEmailAndDeletedAtIsNull("accepted-artist@test.com"));

        StaffInvite invite = staffInviteRepository.findByToken(token).orElseThrow();
        assertNotNull(invite.getAcceptedAt());
    }

    @Test
    void deactivateStaff_withOwnerAuth_setsDeactivatedInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(post("/staff/{id}/deactivate", artist.getId())
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Staff deactivated = staffRepository.findById(artist.getId()).orElseThrow();
        assertEquals(AccountStatus.DEACTIVATED, deactivated.getAccountStatus());
        assertFalse(deactivated.getAvailableForOnlineBooking());
    }

    @Test
    void reactivateStaff_withOwnerAuth_setsActiveInDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());
        artist.setAccountStatus(AccountStatus.DEACTIVATED);
        artist.setAvailableForOnlineBooking(false);
        staffRepository.save(artist);

        mockMvc.perform(post("/staff/{id}/reactivate", artist.getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Staff reactivated = staffRepository.findById(artist.getId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, reactivated.getAccountStatus());
    }

    @Test
    void getFutureAppointmentsCount_withOwnerAuth_returnsCountMatchingDb() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        saveFutureAppointment(bundle, artist, AppointmentStatus.SCHEDULED);
        saveFutureAppointment(bundle, artist, AppointmentStatus.SCHEDULED);
        saveFutureAppointment(bundle, artist, AppointmentStatus.CANCELLED);

        mockMvc.perform(get("/staff/{id}/future-appointments-count", artist.getId())
                        .with(crmUser(bundle.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    void inviteStaff_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        InviteStaffRequest body = InviteStaffRequest.builder()
                .email("invite-target@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(List.of(bundle.location().getId()))
                .build();

        mockMvc.perform(post("/staff/invite")
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivateStaff_withArtistAuth_returnsForbidden() throws Exception {
        TenantBundle bundle = seedTenant();
        Staff artist = IntegrationTestData.seedArtist(staffRepository, bundle.tenant());

        mockMvc.perform(post("/staff/{id}/deactivate", bundle.owner().getId())
                        .with(crmUser(artist))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
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

    private String createInviteAndReturnToken(TenantBundle bundle, String email) throws Exception {
        InviteStaffRequest body = InviteStaffRequest.builder()
                .email(email)
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(List.of(bundle.location().getId()))
                .build();

        String response = mockMvc.perform(post("/staff/invite")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    private Appointment saveFutureAppointment(TenantBundle bundle, Staff artist, AppointmentStatus status) {
        Instant startTime = Instant.now().plus(1, ChronoUnit.DAYS);
        return appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(artist)
                .service(bundle.service())
                .location(bundle.location())
                .startTime(startTime)
                .endTime(startTime.plus(1, ChronoUnit.HOURS))
                .status(status)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());
    }
}
