package com.inkflow.crm.module.onboarding.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.enums.AccountType;
import com.inkflow.crm.domain.enums.SupportedCurrency;
import com.inkflow.crm.domain.enums.SupportedLocale;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.PricingType;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.infrastructure.supabase.SupabaseAdminService;
import com.inkflow.crm.module.email.service.BuiltInTemplateSeeder;
import com.inkflow.crm.module.finance.service.CategoryConfigService;
import com.inkflow.crm.module.onboarding.dto.OnboardingRequest;
import com.inkflow.crm.module.onboarding.dto.OnboardingResponse;
import com.inkflow.crm.module.onboarding.dto.OnboardingServiceDraftDto;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private BuiltInTemplateSeeder builtInTemplateSeeder;

    @Mock
    private CategoryConfigService categoryConfigService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SupabaseAdminService supabaseAdminService;

    @Mock
    private InkflowProperties inkflowProperties;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query advisoryLockQuery;

    @InjectMocks
    private OnboardingService onboardingService;

    @BeforeEach
    void stubOnboardingLock() {
        when(entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))"))
                .thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.setParameter(eq("lockKey"), any())).thenReturn(advisoryLockQuery);
        when(advisoryLockQuery.getSingleResult()).thenReturn(0);
        lenient().when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void completeOnboarding_returnsExistingStaffWithoutCreatingTenant() {
        UUID supabaseUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        Staff existing = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.of(existing));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(Tenant.builder()
                .id(tenantId)
                .name("Existing Studio")
                .build()));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ignored");

        OnboardingResponse response = onboardingService.completeOnboarding(
                supabaseUserId,
                "owner@test.com",
                request
        );

        assertTrue(response.isSuccess());
        assertEquals(staffId, response.getUserId());
        assertEquals(tenantId, response.getTenantId());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void completeOnboarding_createsTenantForNewUser() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        OnboardingResponse response = onboardingService.completeOnboarding(
                supabaseUserId,
                "alex@test.com",
                request
        );

        assertTrue(response.isSuccess());
        assertEquals(UserRole.OWNER.getValue(), response.getRole());
        verify(subscriptionService).createTrialForTenant(any(UUID.class));
        verify(tenantRepository).save(any(Tenant.class));
        verify(staffRepository).save(any(Staff.class));
    }

    @Test
    void completeOnboarding_createsStudioAccountForTeamAccount() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Big Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("team");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals(AccountType.STUDIO, tenantCaptor.getValue().getAccountType());
    }

    @Test
    void completeOnboarding_returnsNullTenantNameWhenTenantMissing() {
        UUID supabaseUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        Staff existing = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.of(existing));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ignored");

        OnboardingResponse response = onboardingService.completeOnboarding(
                supabaseUserId,
                "owner@test.com",
                request
        );

        assertTrue(response.isSuccess());
        assertNull(response.getTenantName());
    }

    @Test
    void completeOnboarding_createsDefaultLocation() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository).save(locationCaptor.capture());
        Location location = locationCaptor.getValue();
        assertEquals("Ink Studio", location.getName());
        assertEquals("#6366f1", location.getColor());
        assertTrue(location.getIsActive());
        assertEquals(LocalTime.of(9, 0), location.getWorkingHoursStart());
        assertEquals(LocalTime.of(22, 0), location.getWorkingHoursEnd());
    }

    @Test
    void completeOnboarding_persistsStaffWithAuthUserIdAndEmail() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Staff> staffCaptor = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).save(staffCaptor.capture());
        Staff owner = staffCaptor.getValue();
        assertEquals(supabaseUserId.toString(), owner.getAuthUserId());
        assertEquals("alex@test.com", owner.getEmail());
        assertEquals("Alex", owner.getFirstName());
        assertEquals(UserRole.OWNER, owner.getRole());
    }

    @Test
    void completeOnboarding_seedsBuiltInTemplates() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        verify(builtInTemplateSeeder).seedDefaultsForTenant(any(UUID.class));
        verify(categoryConfigService).ensureDefaults(any(UUID.class));
    }

    @Test
    void completeOnboarding_syncsSupabaseTenantClaimsForNewUser() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        verify(supabaseAdminService).syncUserTenantClaims(
                eq(supabaseUserId.toString()),
                any(UUID.class),
                eq(UserRole.OWNER.getValue())
        );
    }

    @Test
    void shouldSetSoloAccountTypeWhenTeamSizeIsSoloCaseInsensitive() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("SOLO");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals(AccountType.SOLO, tenantCaptor.getValue().getAccountType());
    }

    @Test
    void shouldSetTenantDefaultsWhenCreatingNewTenant() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        Tenant tenant = tenantCaptor.getValue();
        assertEquals("Ink Studio", tenant.getName());
        assertEquals(SupportedCurrency.UAH, tenant.getCurrency());
        assertEquals("Europe/Kyiv", tenant.getTimezone());
        assertEquals(SupportedLocale.UK, tenant.getLanguage());
        assertTrue(tenant.getIsActive());
    }

    @Test
    void completeOnboarding_persistsCompanySizeForTeamAccount() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Big Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("team");
        request.setCompanySize("4-10");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals("4-10", tenantCaptor.getValue().getCompanySize());
    }

    @Test
    void completeOnboarding_persistsCityServicePhoneAndInstagramForNewUser() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingServiceDraftDto serviceDraft = new OnboardingServiceDraftDto();
        serviceDraft.setTitle("Small tattoo");
        serviceDraft.setPricingType("fixed");
        serviceDraft.setDuration(90);
        serviceDraft.setPrice(new BigDecimal("1800"));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");
        request.setPhone("+380501234567");
        request.setCity("Kyiv");
        request.setInstagram("@inkstudio");
        request.setService(serviceDraft);

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Staff> staffCaptor = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).save(staffCaptor.capture());
        assertEquals("+380501234567", staffCaptor.getValue().getPhone());
        assertEquals("@inkstudio", staffCaptor.getValue().getInstagram());

        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository).save(locationCaptor.capture());
        Location location = locationCaptor.getValue();
        assertEquals("Kyiv", location.getCity());
        assertEquals("", location.getAddress());
        assertEquals("@inkstudio", location.getInstagram());

        ArgumentCaptor<Service> serviceCaptor = ArgumentCaptor.forClass(Service.class);
        verify(serviceRepository).save(serviceCaptor.capture());
        Service service = serviceCaptor.getValue();
        assertEquals("Small tattoo", service.getTitle());
        assertEquals(90, service.getDuration());
        assertEquals(new BigDecimal("1800"), service.getPrice());
        assertEquals(PricingType.FIXED, service.getPricingType());
        assertTrue(service.getIsActive());
    }

    @Test
    void completeOnboarding_createsHourlyServiceWithDefaultDuration() {
        UUID supabaseUserId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> {
            Staff staff = invocation.getArgument(0);
            staff.setId(UUID.randomUUID());
            return staff;
        });
        when(locationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingServiceDraftDto serviceDraft = new OnboardingServiceDraftDto();
        serviceDraft.setTitle("Hourly session");
        serviceDraft.setPricingType("hourly");
        serviceDraft.setPrice(new BigDecimal("1200"));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");
        request.setService(serviceDraft);

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Service> serviceCaptor = ArgumentCaptor.forClass(Service.class);
        verify(serviceRepository).save(serviceCaptor.capture());
        Service service = serviceCaptor.getValue();
        assertEquals("Hourly session", service.getTitle());
        assertEquals(PricingType.HOURLY, service.getPricingType());
        assertEquals(60, service.getDuration());
        assertEquals(new BigDecimal("1200"), service.getPrice());
    }

    @Test
    void shouldSkipProvisioningWhenStaffAlreadyExists() {
        UUID supabaseUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        Staff existing = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString()))
                .thenReturn(Optional.of(existing));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(Tenant.builder()
                .id(tenantId)
                .name("Existing Studio")
                .build()));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ignored");

        onboardingService.completeOnboarding(supabaseUserId, "owner@test.com", request);

        verify(staffRepository, never()).save(any());
        verify(locationRepository, never()).save(any());
        verify(serviceRepository, never()).save(any());
        verify(builtInTemplateSeeder, never()).seedDefaultsForTenant(any());
        verify(subscriptionService, never()).createTrialForTenant(any());
    }
}
