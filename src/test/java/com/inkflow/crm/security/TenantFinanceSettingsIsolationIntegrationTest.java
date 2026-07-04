package com.inkflow.crm.security;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.entity.RolePermission;
import com.inkflow.crm.domain.entity.TransactionCategoryConfig;
import com.inkflow.crm.domain.repository.RolePermissionRepository;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.PaymentMethod;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.module.client.service.ClientDormancyService;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.finance.service.CategoryConfigService;
import com.inkflow.crm.module.payment.dto.ProcessPaymentRequest;
import com.inkflow.crm.module.payment.dto.ProcessRefundRequest;
import com.inkflow.crm.module.payment.service.PaymentService;
import com.inkflow.crm.module.settings.dto.UpdateCompanySettingsRequest;
import com.inkflow.crm.module.settings.dto.UpdateRolePermissionsRequest;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.module.settings.service.SettingsService;
import com.inkflow.crm.module.transaction.dto.CreateTransactionRequest;
import com.inkflow.crm.module.transaction.service.TransactionService;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@Transactional
class TenantFinanceSettingsIsolationIntegrationTest {

    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private CategoryConfigService categoryConfigService;
    @Autowired private TransactionCategoryConfigRepository categoryConfigRepository;
    @Autowired private SettingsService settingsService;
    @Autowired private RolePermissionService rolePermissionService;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @Autowired private ClientRepository clientRepository;
    @Autowired private ClientDormancyService clientDormancyService;

    @Autowired private TenantRepository tenantRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private EntityManager entityManager;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void transactionGetById_rejectsForeignTransaction() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Transaction foreign = seedIncomeTransaction(tenantB);

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> transactionService.getTransactionById(foreign.getId())
        );
    }

    @Test
    void transactionDelete_rejectsForeignTransaction() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Transaction foreign = seedIncomeTransaction(tenantB);

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> transactionService.deleteTransaction(foreign.getId())
        );
        assertNull(transactionRepository.findById(foreign.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void transactionCreate_rejectsForeignLocation() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        categoryConfigService.ensureDefaults(tenantA.tenant().getId());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .type("income")
                .category("service")
                .amount(BigDecimal.valueOf(100))
                .paymentMethod("cash")
                .locationId(tenantB.location().getId())
                .date(Instant.now())
                .build();

        assertThrows(ResourceNotFoundException.class, () -> transactionService.createTransaction(request));
    }

    @Test
    void transactionCreate_rejectsForeignAppointment() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        categoryConfigService.ensureDefaults(tenantA.tenant().getId());
        Appointment foreignAppointment = seedAppointment(tenantB);

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .type("income")
                .category("service")
                .amount(BigDecimal.valueOf(100))
                .paymentMethod("cash")
                .locationId(tenantA.location().getId())
                .appointmentId(foreignAppointment.getId())
                .date(Instant.now())
                .build();

        assertThrows(ResourceNotFoundException.class, () -> transactionService.createTransaction(request));
    }

    @Test
    void getAllTransactions_returnsOnlyCurrentTenantData() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        seedIncomeTransaction(tenantB);

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        var page = transactionService.getAllTransactions(
                new PageRequest(), null, null, null, null, null, null, null, null);
        assertTrue(page.getData().isEmpty());
    }

    @Test
    void processPayment_rejectsForeignAppointment() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Appointment foreignAppointment = seedAppointment(tenantB);

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .appointmentId(foreignAppointment.getId())
                .amount(BigDecimal.valueOf(500))
                .paymentMethod("cash")
                .build();

        assertThrows(ResourceNotFoundException.class, () -> paymentService.processPayment(request));
    }

    @Test
    void processRefund_rejectsForeignTransaction() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Transaction foreign = seedIncomeTransaction(tenantB);

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        ProcessRefundRequest request = ProcessRefundRequest.builder()
                .transactionId(foreign.getId())
                .amount(BigDecimal.valueOf(100))
                .reason("test refund")
                .paymentMethod("cash")
                .build();

        assertThrows(ResourceNotFoundException.class, () -> paymentService.processRefund(request));
    }

    @Test
    void getAppointmentPaymentSummary_rejectsForeignAppointment() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Appointment foreignAppointment = seedAppointment(tenantB);

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> paymentService.getAppointmentPaymentSummary(foreignAppointment.getId())
        );
    }

    @Test
    void categoryGetAll_returnsOnlyCurrentTenantCategories() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        SecurityTestSupport.authenticate(tenantB.owner());
        categoryConfigService.upsert("tenant_b_only", "B Category", "#111111", "EXPENSE");

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());
        categoryConfigService.ensureDefaults(tenantA.tenant().getId());

        var categories = categoryConfigService.getAll();
        assertTrue(categories.stream().noneMatch(c -> "tenant_b_only".equals(c.getCategoryKey())));
    }

    @Test
    void categoryUpsert_doesNotMutateForeignTenantCategory() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        SecurityTestSupport.authenticate(tenantB.owner());
        categoryConfigService.upsert("shared_key", "B Label", "#111111", "EXPENSE");
        TransactionCategoryConfig foreignCategory = categoryConfigRepository
                .findByCategoryKeyAndDeletedAtIsNull("shared_key")
                .orElseThrow();

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());
        categoryConfigService.ensureDefaults(tenantA.tenant().getId());
        categoryConfigService.upsert("shared_key", "A Label", "#222222", "EXPENSE");

        TransactionCategoryConfig unchanged = categoryConfigRepository.findById(foreignCategory.getId()).orElseThrow();
        assertEquals("B Label", unchanged.getLabel());
        assertEquals(tenantB.tenant().getId(), unchanged.getTenantId());
    }

    @Test
    void getCompanySettings_returnsCurrentTenantOnly() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        var settings = settingsService.getCompanySettings();
        assertEquals(tenantA.tenant().getName(), settings.getName());
        assertNotEquals(tenantB.tenant().getName(), settings.getName());
    }

    @Test
    void updateCompanySettings_doesNotMutateForeignTenant() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        String originalBName = "Original B " + UUID.randomUUID();
        tenantB.tenant().setName(originalBName);
        tenantRepository.save(tenantB.tenant());

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());

        UpdateCompanySettingsRequest updateRequest = new UpdateCompanySettingsRequest();
        updateRequest.setName("Hacked by A");
        settingsService.updateCompanySettings(updateRequest);

        Tenant tenantBReloaded = tenantRepository.findById(tenantB.tenant().getId()).orElseThrow();
        assertEquals(originalBName, tenantBReloaded.getName());
    }

    @Test
    void updateRolePermissions_doesNotMutateForeignTenantPermissions() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        SecurityTestSupport.authenticate(tenantB.owner());
        rolePermissionService.getAllRolePermissions();
        RolePermission foreignPermission = rolePermissionRepository.findByRole(UserRole.ARTIST).stream()
                .filter(RolePermission::getGranted)
                .findFirst()
                .orElseThrow();

        PersistenceTestSupport.clearPersistenceContext(entityManager);
        SecurityTestSupport.authenticate(tenantA.owner());
        rolePermissionService.getAllRolePermissions();

        UpdateRolePermissionsRequest request = new UpdateRolePermissionsRequest();
        request.setPermissions(List.of(Permission.CLIENTS_DELETE.getValue()));
        rolePermissionService.updateRolePermissions(UserRole.ARTIST.getValue(), request);

        RolePermission unchanged = rolePermissionRepository.findById(foreignPermission.getId()).orElseThrow();
        assertEquals(tenantB.tenant().getId(), unchanged.getTenantId());
        assertTrue(unchanged.getGranted());
    }

    @Test
    void processDormancy_marksOnlyCurrentTenantClients() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        Instant oldVisit = Instant.now().minus(100, ChronoUnit.DAYS);

        Client clientA = tenantA.client();
        clientA.setLastVisit(oldVisit);
        clientA.setDormant(false);
        clientRepository.save(clientA);

        Client clientB = tenantB.client();
        clientB.setLastVisit(oldVisit);
        clientB.setDormant(false);
        clientRepository.save(clientB);

        PersistenceTestSupport.clearPersistenceContext(entityManager);

        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        clientDormancyService.processDormancy(tenantA.tenant().getId(), cutoff);

        Client reloadedA = clientRepository.findById(clientA.getId()).orElseThrow();
        Client reloadedB = clientRepository.findById(clientB.getId()).orElseThrow();
        assertTrue(reloadedA.isDormant());
        assertFalse(reloadedB.isDormant());
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

    private Appointment seedAppointment(TenantBundle bundle) {
        return appointmentRepository.save(Appointment.builder()
                .tenantId(bundle.tenant().getId())
                .client(bundle.client())
                .artist(bundle.owner())
                .service(bundle.service())
                .location(bundle.location())
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .status(AppointmentStatus.SCHEDULED)
                .price(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .build());
    }

    private Transaction seedIncomeTransaction(TenantBundle bundle) {
        categoryConfigService.ensureDefaults(bundle.tenant().getId());
        return transactionRepository.save(Transaction.builder()
                .tenantId(bundle.tenant().getId())
                .type(TransactionType.INCOME)
                .category("service")
                .paymentType(PaymentType.SERVICE_PAYMENT)
                .amount(BigDecimal.valueOf(500))
                .paymentMethod(PaymentMethod.CASH)
                .location(bundle.location())
                .staff(bundle.owner())
                .date(Instant.now())
                .refundedAmount(BigDecimal.ZERO)
                .isRefunded(false)
                .build());
    }
}
