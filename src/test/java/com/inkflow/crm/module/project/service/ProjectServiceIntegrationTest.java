package com.inkflow.crm.module.project.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
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
import com.inkflow.crm.module.project.dto.ProjectDto;
import com.inkflow.crm.module.project.dto.ProjectFilterRequest;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@Transactional
class ProjectServiceIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

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

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void shouldExcludeProjectWithoutAnyLocationLinkWhenFilteringByLocation() {
        TenantBundle bundle = seedTenant();
        SecurityTestSupport.authenticate(bundle.owner());

        Project orphanProject = projectRepository.save(Project.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(bundle.owner())
                .title("Unlinked")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(1000))
                .totalSessions(1)
                .completedSessions(0)
                .totalPaid(BigDecimal.ZERO)
                .build());

        PageResult<ProjectDto> scoped = projectService.getAllProjects(pageRequest(), new ProjectFilterRequest(), bundle.location().getId());

        assertFalse(containsProject(scoped, orphanProject.getId()));
    }

    @Test
    void shouldIncludeProjectWhenLeadArtistWorksAtLocation() {
        TenantBundle bundle = seedTenant();
        Location locationB = saveLocation(bundle, "Studio B");
        Staff leadArtist = assignLocations(bundle.owner(), bundle.location());
        SecurityTestSupport.authenticate(bundle.owner());

        Project project = projectRepository.save(Project.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(leadArtist)
                .title("Lead at A")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(1000))
                .totalSessions(1)
                .completedSessions(0)
                .totalPaid(BigDecimal.ZERO)
                .build());

        PageResult<ProjectDto> atA = projectService.getAllProjects(pageRequest(), new ProjectFilterRequest(), bundle.location().getId());
        PageResult<ProjectDto> atB = projectService.getAllProjects(pageRequest(), new ProjectFilterRequest(), locationB.getId());

        assertTrue(containsProject(atA, project.getId()));
        assertFalse(containsProject(atB, project.getId()));
    }

    @Test
    void shouldIncludeProjectInBothLocationsWhenSessionArtistWorksAtSecondLocation() {
        TenantBundle bundle = seedTenant();
        Location locationA = bundle.location();
        Location locationB = saveLocation(bundle, "Studio B");

        Staff leadArtist = assignLocations(bundle.owner(), locationA);
        Staff guestArtist = assignLocations(
                IntegrationTestData.seedArtist(staffRepository, bundle.tenant()),
                locationB
        );
        SecurityTestSupport.authenticate(bundle.owner());

        Project project = projectRepository.save(Project.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(leadArtist)
                .title("Cross-location")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .completedSessions(0)
                .totalPaid(BigDecimal.ZERO)
                .build());

        appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(guestArtist)
                .service(bundle.service())
                .location(locationA)
                .project(project)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());

        PageResult<ProjectDto> atA = projectService.getAllProjects(pageRequest(), new ProjectFilterRequest(), locationA.getId());
        PageResult<ProjectDto> atB = projectService.getAllProjects(pageRequest(), new ProjectFilterRequest(), locationB.getId());

        assertTrue(containsProject(atA, project.getId()));
        assertTrue(containsProject(atB, project.getId()));
    }

    private static boolean containsProject(PageResult<ProjectDto> result, UUID projectId) {
        return result.getData().stream().anyMatch(dto -> projectId.equals(dto.getId()));
    }

    private static PageRequest pageRequest() {
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(0);
        pageRequest.setSize(50);
        return pageRequest;
    }

    private Location saveLocation(TenantBundle bundle, String name) {
        return locationRepository.save(Location.builder()
                .tenantId(bundle.tenant().getId())
                .name(name)
                .address("Kyiv")
                .color("#22c55e")
                .isActive(true)
                .build());
    }

    private Staff assignLocations(Staff staff, Location... locations) {
        staff.setLocations(new HashSet<>(Set.of(locations)));
        return staffRepository.save(staff);
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
