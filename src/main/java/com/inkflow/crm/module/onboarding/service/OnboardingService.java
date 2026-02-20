package com.inkflow.crm.module.onboarding.service;

import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.onboarding.dto.OnboardingRequest;
import com.inkflow.crm.module.onboarding.dto.OnboardingResponse;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final TenantRepository tenantRepository;
    private final StaffRepository staffRepository;
    private final LocationRepository locationRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final SubscriptionService subscriptionService;

    @Transactional
    public OnboardingResponse completeOnboarding(UUID supabaseUserId, String email, OnboardingRequest request) {
        log.info("Starting onboarding for user: {}", email);

        // 1. Create Tenant
        Tenant tenant = Tenant.builder()
                .name(request.getCompanyName())
                .subdomain(generateSubdomain(request.getCompanyName()))
                .currency("UAH")
                .timezone("Europe/Kyiv")
                .language("ua")
                .isActive(true)
                .build();
        tenant = tenantRepository.save(tenant);
        log.info("Created tenant: {}", tenant.getId());

        // 2. Create Owner Staff
        Staff owner = Staff.builder()
                .authUserId(supabaseUserId.toString())
                .tenantId(tenant.getId())
                .email(email)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(UserRole.OWNER)
                .status(StaffStatus.WORKING)
                .calendarColor("#6366f1")
                .build();
        owner = staffRepository.save(owner);
        log.info("Created owner staff: {}", owner.getId());

        // 3. Create default Location
        Location defaultLocation = Location.builder()
                .tenantId(tenant.getId())
                .name("Основна студія")
                .address("")
                .phone("")
                .color("#6366f1")
                .isActive(true)
                .build();
        locationRepository.save(defaultLocation);

        // 4. Create default CompanySettings
        CompanySettings settings = CompanySettings.builder()
                .tenant(tenant)
                .smsReminders(false)
                .telegramReminders(false)
                .emailReminders(true)
                .reminderHoursBefore(24)
                .workingHoursStart(LocalTime.of(10, 0))
                .workingHoursEnd(LocalTime.of(20, 0))
                .allowOnlineBooking(false)
                .minAdvanceHours(24)
                .maxAdvanceDays(60)
                .build();
        companySettingsRepository.save(settings);

        // 5. Create 14-day free trial subscription
        subscriptionService.createTrialForTenant(tenant.getId());

        return OnboardingResponse.builder()
                .userId(owner.getId())
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .role(UserRole.OWNER.getValue())
                .success(true)
                .build();
    }

    private String generateSubdomain(String companyName) {
        String base = companyName.toLowerCase()
                .replaceAll("[^a-z0-9]", "");

        if (base.length() > 20) {
            base = base.substring(0, 20);
        }

        if (base.isEmpty()) {
            base = "studio";
        }

        return base + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
