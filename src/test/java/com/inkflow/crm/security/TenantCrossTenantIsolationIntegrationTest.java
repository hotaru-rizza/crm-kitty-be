package com.inkflow.crm.security;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.ArtistServicePricing;
import com.inkflow.crm.domain.entity.StaffFaq;
import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.domain.entity.GalleryPhoto;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.TransactionCategoryConfig;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.GalleryStage;
import com.inkflow.crm.domain.enums.LeaveStatus;
import com.inkflow.crm.domain.enums.LeaveType;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ArtistServicePricingRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.EmailTemplateRepository;
import com.inkflow.crm.domain.repository.GalleryPhotoRepository;
import com.inkflow.crm.domain.repository.LeaveRequestRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffFaqRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.module.email.dto.CreateEmailTemplateRequest;
import com.inkflow.crm.module.email.dto.UpdateEmailTemplateRequest;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.service.EmailTemplateService;
import com.inkflow.crm.module.finance.service.CategoryConfigService;
import com.inkflow.crm.module.leave.service.LeaveService;
import com.inkflow.crm.module.location.dto.AssignStaffRequest;
import com.inkflow.crm.module.location.service.LocationService;
import com.inkflow.crm.module.notification.entity.Notification;
import com.inkflow.crm.module.notification.entity.NotificationChannel;
import com.inkflow.crm.module.notification.entity.NotificationType;
import com.inkflow.crm.module.notification.repository.NotificationRepository;
import com.inkflow.crm.module.notification.service.NotificationService;
import com.inkflow.crm.module.project.service.ProjectService;
import com.inkflow.crm.module.request.service.RequestService;
import com.inkflow.crm.module.service.service.ServiceService;
import com.inkflow.crm.module.staff.dto.UpdateStaffServicesRequest;
import com.inkflow.crm.module.staff.dto.UpsertFaqRequest;
import com.inkflow.crm.module.staff.service.StaffDetailService;
import com.inkflow.crm.module.staff.service.StaffFaqService;
import com.inkflow.crm.module.staff.service.StaffPricingService;
import com.inkflow.crm.module.appointment.service.AppointmentService;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.PersistenceTestSupport;
import com.inkflow.crm.support.SecurityTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-tenant isolation regression suite for high-risk service entry points.
 * Each test seeds data in tenant B and asserts tenant A cannot read or mutate it.
 */
@IntegrationTest
@Transactional
class TenantCrossTenantIsolationIntegrationTest {

    @Autowired private EmailTemplateService emailTemplateService;
    @Autowired private EmailTemplateRepository emailTemplateRepository;
    @Autowired private LocationService locationService;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ProjectService projectService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private GalleryPhotoRepository galleryPhotoRepository;
    @Autowired private AppointmentService appointmentService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private StaffDetailService staffDetailService;
    @Autowired private StaffFaqService staffFaqService;
    @Autowired private StaffPricingService staffPricingService;
    @Autowired private ServiceService serviceService;
    @Autowired private LeaveService leaveService;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private RequestService requestService;
    @Autowired private RequestRepository requestRepository;
    @Autowired private CategoryConfigService categoryConfigService;
    @Autowired private TransactionCategoryConfigRepository categoryConfigRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;

    @Autowired private TenantRepository tenantRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private StaffFaqRepository staffFaqRepository;
    @Autowired private ArtistServicePricingRepository artistServicePricingRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private EntityManager entityManager;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void emailTemplateUpdate_rejectsForeignTemplate() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        EmailTemplate foreignTemplate = emailTemplateRepository.save(EmailTemplate.builder()
                .tenantId(tenantB.tenant().getId())
                .triggerType(TriggerType.MANUAL)
                .subject("Foreign")
                .body("Body")
                .enabled(true)
                .deletable(true)
                .category(TriggerType.MANUAL.getCategory())
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                BusinessRuleException.class,
                () -> emailTemplateService.update(
                        tenantA.tenant().getId(),
                        foreignTemplate.getId(),
                        new UpdateEmailTemplateRequest(null, null, "Hacked", null, null),
                        tenantA.owner().getId()
                )
        );
        assertEquals("Foreign", emailTemplateRepository.findById(foreignTemplate.getId()).orElseThrow().getSubject());
    }

    @Test
    void emailTemplateDelete_rejectsForeignTemplate() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        EmailTemplate foreignTemplate = emailTemplateRepository.save(EmailTemplate.builder()
                .tenantId(tenantB.tenant().getId())
                .triggerType(TriggerType.MANUAL)
                .subject("Keep me")
                .body("Body")
                .enabled(true)
                .deletable(true)
                .category(TriggerType.MANUAL.getCategory())
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                BusinessRuleException.class,
                () -> emailTemplateService.delete(tenantA.tenant().getId(), foreignTemplate.getId())
        );
        assertTrue(emailTemplateRepository.findById(foreignTemplate.getId()).isPresent());
    }

    @Test
    void emailTemplatePreview_rejectsForeignTemplate() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        EmailTemplate foreignTemplate = emailTemplateRepository.save(EmailTemplate.builder()
                .tenantId(tenantB.tenant().getId())
                .triggerType(TriggerType.MANUAL)
                .subject("Preview")
                .body("Body")
                .enabled(true)
                .deletable(true)
                .category(TriggerType.MANUAL.getCategory())
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                BusinessRuleException.class,
                () -> emailTemplateService.preview(tenantA.tenant().getId(), foreignTemplate.getId())
        );
    }

    @Test
    void locationAssignStaff_ignoresForeignStaffIds() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        locationService.assignStaff(
                tenantA.location().getId(),
                AssignStaffRequest.builder().staffIds(List.of(tenantB.owner().getId())).build()
        );

        var location = locationRepository.findByIdAndDeletedAtIsNull(tenantA.location().getId()).orElseThrow();
        assertTrue(location.getStaff().isEmpty());
    }

    @Test
    void locationGetById_rejectsForeignLocation() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> locationService.getLocationById(tenantB.location().getId())
        );
    }

    @Test
    void projectGetById_rejectsForeignProject() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Project foreignProject = seedProject(tenantB);

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> projectService.getProjectById(foreignProject.getId())
        );
    }

    @Test
    void projectDeletePhoto_rejectsForeignPhoto() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Project foreignProject = seedProject(tenantB);
        GalleryPhoto foreignPhoto = galleryPhotoRepository.save(GalleryPhoto.builder()
                .tenantId(tenantB.tenant().getId())
                .project(foreignProject)
                .url("https://cdn.example.com/photo.jpg")
                .stage(GalleryStage.FRESH)
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> projectService.deletePhoto(foreignProject.getId(), foreignPhoto.getId())
        );
        assertTrue(galleryPhotoRepository.findById(foreignPhoto.getId()).isPresent());
    }

    @Test
    void appointmentDeletePhoto_rejectsForeignPhoto() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Appointment foreignAppointment = seedAppointment(tenantB);
        GalleryPhoto foreignPhoto = galleryPhotoRepository.save(GalleryPhoto.builder()
                .tenantId(tenantB.tenant().getId())
                .appointment(foreignAppointment)
                .url("https://cdn.example.com/appt-photo.jpg")
                .stage(GalleryStage.IN_PROGRESS)
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> appointmentService.deletePhoto(foreignAppointment.getId(), foreignPhoto.getId())
        );
        assertTrue(galleryPhotoRepository.findById(foreignPhoto.getId()).isPresent());
    }

    @Test
    void staffDetail_rejectsForeignStaff() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> staffDetailService.getDetail(tenantB.owner().getId())
        );
    }

    @Test
    void upsertFaq_rejectsForeignStaffAndPreservesForeignData() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        staffFaqRepository.save(StaffFaq.builder()
                .staffId(tenantB.owner().getId())
                .question("Foreign FAQ?")
                .answer("Foreign answer")
                .sortOrder(0)
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        UpsertFaqRequest request = UpsertFaqRequest.builder()
                .items(List.of(new UpsertFaqRequest.FaqItem("Hacked?", "No")))
                .build();

        assertThrows(
                ResourceNotFoundException.class,
                () -> staffFaqService.upsertFaq(tenantB.owner().getId(), request)
        );

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        assertEquals(1, staffFaqRepository.findByStaffIdOrderBySortOrderAsc(tenantB.owner().getId()).size());
        assertEquals("Foreign FAQ?", staffFaqRepository.findByStaffIdOrderBySortOrderAsc(tenantB.owner().getId()).getFirst().getQuestion());
    }

    @Test
    void updateStaffServices_rejectsForeignStaffAndPreservesForeignPricing() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        artistServicePricingRepository.save(ArtistServicePricing.builder()
                .staff(tenantB.owner())
                .service(tenantB.service())
                .price(BigDecimal.valueOf(500))
                .duration(90)
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        UpdateStaffServicesRequest request = UpdateStaffServicesRequest.builder()
                .services(List.of(UpdateStaffServicesRequest.ServiceAssignment.builder()
                        .serviceId(tenantA.service().getId())
                        .customPrice(BigDecimal.valueOf(100))
                        .customDuration(60)
                        .build()))
                .build();

        assertThrows(
                ResourceNotFoundException.class,
                () -> staffPricingService.updateStaffServices(tenantB.owner().getId(), request)
        );

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        List<ArtistServicePricing> foreignPricing = artistServicePricingRepository.findByStaffId(tenantB.owner().getId());
        assertEquals(1, foreignPricing.size());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(foreignPricing.getFirst().getPrice()));
    }

    @Test
    void serviceGetById_rejectsForeignService() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> serviceService.getServiceById(tenantB.service().getId())
        );
    }

    @Test
    void leaveGetById_rejectsForeignLeave() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        LeaveRequest foreignLeave = leaveRequestRepository.save(LeaveRequest.builder()
                .tenantId(tenantB.tenant().getId())
                .staff(tenantB.owner())
                .leaveType(LeaveType.VACATION)
                .status(LeaveStatus.PENDING)
                .startDate(LocalDate.now().plusDays(5))
                .endDate(LocalDate.now().plusDays(7))
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> leaveService.getLeaveById(foreignLeave.getId())
        );
    }

    @Test
    void requestGetById_rejectsForeignRequest() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Request foreignRequest = requestRepository.save(Request.builder()
                .tenantId(tenantB.tenant().getId())
                .source(RequestSource.WEBSITE)
                .clientName("Foreign Client")
                .status(RequestStatus.NEW)
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> requestService.getRequestById(foreignRequest.getId())
        );
    }

    @Test
    void categoryDelete_doesNotAffectForeignCategory() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        TransactionCategoryConfig foreignCategory = categoryConfigRepository.save(TransactionCategoryConfig.builder()
                .tenantId(tenantB.tenant().getId())
                .categoryKey("foreign_key")
                .label("Foreign")
                .color("#000000")
                .plType("EXPENSE")
                .isDefault(false)
                .isActive(true)
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        categoryConfigService.delete(foreignCategory.getId());

        TransactionCategoryConfig stillThere = categoryConfigRepository.findById(foreignCategory.getId()).orElseThrow();
        assertNull(stillThere.getDeletedAt());
    }

    @Test
    void notificationMarkAsRead_doesNotMarkForeignNotification() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        Notification foreignNotification = notificationRepository.save(Notification.builder()
                .tenantId(tenantB.tenant().getId())
                .recipientId(tenantB.owner().getId())
                .channel(NotificationChannel.IN_APP)
                .type(NotificationType.SYSTEM)
                .title("Foreign")
                .body("Body")
                .build());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        notificationService.markAsRead(foreignNotification.getId());

        Notification unchanged = notificationRepository.findById(foreignNotification.getId()).orElseThrow();
        assertFalse(unchanged.getIsRead());
    }

    @Test
    void emailTemplateList_returnsOnlyCurrentTenantTemplates() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        emailTemplateService.create(
                tenantB.tenant().getId(),
                new CreateEmailTemplateRequest(TriggerType.MANUAL, null, "B only", "Body", true),
                tenantB.owner().getId()
        );
        emailTemplateService.create(
                tenantA.tenant().getId(),
                new CreateEmailTemplateRequest(TriggerType.MANUAL, null, "A only", "Body", true),
                tenantA.owner().getId()
        );

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        var templates = emailTemplateService.list(tenantA.tenant().getId());
        assertEquals(1, templates.size());
        assertEquals("A only", templates.getFirst().subject());
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

    private Project seedProject(TenantBundle bundle) {
        return projectRepository.save(Project.builder()
                .tenantId(bundle.tenant().getId())
                .title("Foreign project")
                .client(bundle.client())
                .artist(bundle.owner())
                .location(bundle.location())
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(500))
                .totalPaid(BigDecimal.ZERO)
                .totalSessions(1)
                .completedSessions(0)
                .build());
    }

    private Appointment seedAppointment(TenantBundle bundle) {
        return appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(bundle.owner())
                .service(bundle.service())
                .location(bundle.location())
                .startTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());
    }
}
