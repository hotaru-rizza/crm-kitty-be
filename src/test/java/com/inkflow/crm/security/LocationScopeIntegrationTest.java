package com.inkflow.crm.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.transaction.dto.CreateTransactionRequest;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.crmUser;
import static com.inkflow.crm.support.SecurityTestSupport.crmUserWithLocations;
import static com.inkflow.crm.support.SecurityTestSupport.entityScopeHeader;
import static com.inkflow.crm.support.SecurityTestSupport.locationHeader;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class LocationScopeIntegrationTest {

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
    private TransactionRepository transactionRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void shouldReturnOnlyLocationAAppointmentsWhenHeaderIsA() throws Exception {
        TwoLocationFixture fixture = seedTwoLocationFixture();

        String response = mockMvc.perform(get("/appointments")
                        .param("from", fixture.rangeFrom().toString())
                        .param("to", fixture.rangeTo().toString())
                        .with(crmUser(fixture.bundle().owner()))
                        .with(locationHeader(fixture.locationA().getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<UUID> ids = extractIds(response);
        assertTrue(ids.contains(fixture.appointmentAtA().getId()));
        assertFalse(ids.contains(fixture.appointmentAtB().getId()));
    }

    @Test
    void shouldReturnOnlyLocationBAppointmentsWhenHeaderIsB() throws Exception {
        TwoLocationFixture fixture = seedTwoLocationFixture();

        String response = mockMvc.perform(get("/appointments")
                        .param("from", fixture.rangeFrom().toString())
                        .param("to", fixture.rangeTo().toString())
                        .with(crmUser(fixture.bundle().owner()))
                        .with(locationHeader(fixture.locationB().getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<UUID> ids = extractIds(response);
        assertFalse(ids.contains(fixture.appointmentAtA().getId()));
        assertTrue(ids.contains(fixture.appointmentAtB().getId()));
    }

    @Test
    void shouldReturnAllLocationAppointmentsWhenHeaderMissing() throws Exception {
        TwoLocationFixture fixture = seedTwoLocationFixture();

        String response = mockMvc.perform(get("/appointments")
                        .param("from", fixture.rangeFrom().toString())
                        .param("to", fixture.rangeTo().toString())
                        .with(crmUser(fixture.bundle().owner())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<UUID> ids = extractIds(response);
        assertTrue(ids.contains(fixture.appointmentAtA().getId()));
        assertTrue(ids.contains(fixture.appointmentAtB().getId()));
    }

    @Test
    void shouldRejectArtistWhenLocationHeaderIsNotAssigned() throws Exception {
        TwoLocationFixture fixture = seedTwoLocationFixture();
        Staff artist = assignLocations(
                IntegrationTestData.seedArtist(staffRepository, fixture.bundle().tenant()),
                fixture.locationA()
        );

        mockMvc.perform(get("/appointments")
                        .param("from", fixture.rangeFrom().toString())
                        .param("to", fixture.rangeTo().toString())
                        .with(crmUserWithLocations(artist, List.of(fixture.locationA().getId())))
                        .with(locationHeader(fixture.locationB().getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldScopeCalendarQueryByLocationHeader() throws Exception {
        TwoLocationFixture fixture = seedTwoLocationFixture();

        String response = mockMvc.perform(get("/appointments/calendar")
                        .param("from", fixture.rangeFrom().toString())
                        .param("to", fixture.rangeTo().toString())
                        .with(crmUser(fixture.bundle().owner()))
                        .with(locationHeader(fixture.locationA().getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<UUID> ids = extractIds(response);
        assertTrue(ids.contains(fixture.appointmentAtA().getId()));
        assertFalse(ids.contains(fixture.appointmentAtB().getId()));
    }

    @Test
    void shouldScopeTransactionsByLocationHeader() throws Exception {
        TwoLocationFixture fixture = seedTwoLocationFixture();
        createTransaction(fixture.bundle(), fixture.locationA(), BigDecimal.valueOf(1000));
        createTransaction(fixture.bundle(), fixture.locationB(), BigDecimal.valueOf(2000));

        String response = mockMvc.perform(get("/transactions")
                        .with(crmUser(fixture.bundle().owner()))
                        .with(locationHeader(fixture.locationA().getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<UUID> ids = extractIds(response);
        assertEquals(1, ids.size());
        assertTrue(transactionRepository.findAll().stream()
                .anyMatch(tx -> ids.contains(tx.getId())
                        && fixture.locationA().getId().equals(tx.getLocation().getId())));
    }

    @Test
    void shouldScopeProjectsByLocationHeader() throws Exception {
        TwoLocationFixture fixture = seedTwoLocationFixture();
        Staff leadAtA = assignLocations(fixture.bundle().owner(), fixture.locationA());

        Project projectAtA = projectRepository.save(Project.builder()
                .tenantId(fixture.bundle().tenant().getId())
                .client(fixture.bundle().client())
                .artist(leadAtA)
                .title("Project at A")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(1000))
                .totalSessions(1)
                .completedSessions(0)
                .totalPaid(BigDecimal.ZERO)
                .build());

        String atA = mockMvc.perform(get("/projects")
                        .with(crmUser(fixture.bundle().owner()))
                        .with(locationHeader(fixture.locationA().getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String atB = mockMvc.perform(get("/projects")
                        .with(crmUser(fixture.bundle().owner()))
                        .with(locationHeader(fixture.locationB().getId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(extractIds(atA).contains(projectAtA.getId()));
        assertFalse(extractIds(atB).contains(projectAtA.getId()));
    }

    @Test
    void shouldIgnoreWorkspaceLocationHeaderWhenEntityScopeIsTrue() throws Exception {
        TwoLocationFixture fixture = seedTwoLocationFixture();

        String response = mockMvc.perform(get("/appointments")
                        .param("from", fixture.rangeFrom().toString())
                        .param("to", fixture.rangeTo().toString())
                        .with(crmUser(fixture.bundle().owner()))
                        .with(locationHeader(fixture.locationA().getId()))
                        .with(entityScopeHeader()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<UUID> ids = extractIds(response);
        assertTrue(ids.contains(fixture.appointmentAtA().getId()));
        assertTrue(ids.contains(fixture.appointmentAtB().getId()));
    }

    private TwoLocationFixture seedTwoLocationFixture() {
        TenantBundle bundle = IntegrationTestData.seedTenant(
                tenantRepository, staffRepository, clientRepository, serviceRepository, locationRepository);
        Location locationA = bundle.location();
        Location locationB = locationRepository.save(Location.builder()
                .tenantId(bundle.tenant().getId())
                .name("Studio B")
                .address("Lviv")
                .color("#22c55e")
                .isActive(true)
                .build());

        Instant base = Instant.now().plus(10, ChronoUnit.DAYS);
        Appointment appointmentAtA = saveAppointment(bundle, locationA, base);
        Appointment appointmentAtB = saveAppointment(bundle, locationB, base.plus(1, ChronoUnit.DAYS));

        return new TwoLocationFixture(
                bundle,
                locationA,
                locationB,
                appointmentAtA,
                appointmentAtB,
                base.minus(1, ChronoUnit.DAYS),
                base.plus(3, ChronoUnit.DAYS)
        );
    }

    private Appointment saveAppointment(TenantBundle bundle, Location location, Instant start) {
        return appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(bundle.owner())
                .service(bundle.service())
                .location(location)
                .startTime(start)
                .endTime(start.plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());
    }

    private void createTransaction(TenantBundle bundle, Location location, BigDecimal amount) throws Exception {
        Instant transactionDate = LocalDate.now()
                .atTime(10, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant();

        CreateTransactionRequest body = CreateTransactionRequest.builder()
                .type("income")
                .category("service")
                .amount(amount)
                .paymentMethod("cash")
                .locationId(location.getId())
                .date(transactionDate)
                .description("Scoped transaction")
                .build();

        mockMvc.perform(post("/transactions")
                        .with(crmUser(bundle.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private Staff assignLocations(Staff staff, Location... locations) {
        staff.setLocations(new HashSet<>(Set.of(locations)));
        return staffRepository.save(staff);
    }

    private List<UUID> extractIds(String response) throws Exception {
        JsonNode data = objectMapper.readTree(response).path("data");
        List<UUID> ids = new ArrayList<>();
        if (!data.isArray()) {
            return ids;
        }
        for (JsonNode node : data) {
            ids.add(UUID.fromString(node.path("id").asText()));
        }
        return ids;
    }

    private record TwoLocationFixture(
            TenantBundle bundle,
            Location locationA,
            Location locationB,
            Appointment appointmentAtA,
            Appointment appointmentAtB,
            Instant rangeFrom,
            Instant rangeTo
    ) {
    }
}
