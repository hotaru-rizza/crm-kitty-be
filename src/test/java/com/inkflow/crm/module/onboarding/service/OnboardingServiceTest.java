package com.inkflow.crm.module.onboarding.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.onboarding.dto.OnboardingRequest;
import com.inkflow.crm.module.onboarding.dto.OnboardingResponse;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private CompanySettingsRepository companySettingsRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private InkflowProperties inkflowProperties;

    @InjectMocks
    private OnboardingService onboardingService;

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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Big Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("team");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals("STUDIO", tenantCaptor.getValue().getAccountType());
    }

    @Test
    void completeOnboarding_sanitizesSubdomainFromCompanyName() {
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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink & Flow Studio!!!");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertTrue(tenantCaptor.getValue().getSubdomain().startsWith("inkflowstudio-"));
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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Location> locationCaptor = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository).save(locationCaptor.capture());
        Location location = locationCaptor.getValue();
        assertEquals("Основна студія", location.getName());
        assertEquals("#6366f1", location.getColor());
        assertTrue(location.getIsActive());
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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

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
    void completeOnboarding_createsCompanySettingsWithDefaults() {
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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<CompanySettings> settingsCaptor = ArgumentCaptor.forClass(CompanySettings.class);
        verify(companySettingsRepository).save(settingsCaptor.capture());
        CompanySettings settings = settingsCaptor.getValue();
        assertTrue(settings.getEmailReminders());
        assertEquals(false, settings.getSmsReminders());
        assertEquals(24, settings.getReminderHoursBefore());
        assertEquals(LocalTime.of(10, 0), settings.getWorkingHoursStart());
        assertEquals(LocalTime.of(20, 0), settings.getWorkingHoursEnd());
        assertEquals(false, settings.getAllowOnlineBooking());
    }

    @Test
    void shouldUseStudioSubdomainFallbackWhenCompanyNameHasNoAlphanumerics() {
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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("!!! @@@");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertTrue(tenantCaptor.getValue().getSubdomain().startsWith("studio-"));
    }

    @Test
    void shouldTruncateSubdomainBaseWhenCompanyNameExceedsTwentyChars() {
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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("abcdefghijklmnopqrstuvwxyz");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("solo");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertTrue(tenantCaptor.getValue().getSubdomain().startsWith("abcdefghijklmnopqrst-"));
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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingRequest request = new OnboardingRequest();
        request.setCompanyName("Ink Studio");
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setTeamSize("SOLO");

        onboardingService.completeOnboarding(supabaseUserId, "alex@test.com", request);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals("SOLO", tenantCaptor.getValue().getAccountType());
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
        when(companySettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

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
        assertEquals("UAH", tenant.getCurrency());
        assertEquals("Europe/Kyiv", tenant.getTimezone());
        assertEquals("ua", tenant.getLanguage());
        assertTrue(tenant.getIsActive());
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
        verify(companySettingsRepository, never()).save(any());
        verify(subscriptionService, never()).createTrialForTenant(any());
    }
}
