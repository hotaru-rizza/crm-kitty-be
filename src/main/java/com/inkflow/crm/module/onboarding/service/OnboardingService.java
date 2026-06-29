package com.inkflow.crm.module.onboarding.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.AccountType;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.SupportedCurrency;
import com.inkflow.crm.domain.enums.SupportedLocale;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.email.service.BuiltInTemplateSeeder;
import com.inkflow.crm.module.onboarding.dto.OnboardingRequest;
import com.inkflow.crm.module.onboarding.dto.OnboardingResponse;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final TenantRepository tenantRepository;
    private final StaffRepository staffRepository;
    private final LocationRepository locationRepository;
    private final BuiltInTemplateSeeder builtInTemplateSeeder;
    private final SubscriptionService subscriptionService;
    private final InkflowProperties inkflowProperties;

    @Transactional
    public OnboardingResponse completeOnboarding(UUID supabaseUserId, String email, OnboardingRequest request) {
        return staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString())
                .map(this::toExistingResponse)
                .orElseGet(() -> createNewTenant(supabaseUserId, email, request));
    }

    private OnboardingResponse toExistingResponse(Staff existing) {
        log.info("Onboarding skipped — user already has staff record: {}", existing.getId());
        return OnboardingResponse.builder()
                .userId(existing.getId())
                .tenantId(existing.getTenantId())
                .tenantName(tenantRepository.findById(existing.getTenantId())
                        .map(Tenant::getName)
                        .orElse(null))
                .role(existing.getRole().getValue())
                .success(true)
                .build();
    }

    private OnboardingResponse createNewTenant(UUID supabaseUserId, String email, OnboardingRequest request) {
        log.info("Starting onboarding for user: {}", email);

        AccountType accountType = "solo".equalsIgnoreCase(request.getTeamSize())
                ? AccountType.SOLO
                : AccountType.STUDIO;

        Tenant tenant = Tenant.builder()
                .name(request.getCompanyName())
                .currency(SupportedCurrency.UAH)
                .timezone(inkflowProperties.getDefaultTimezone())
                .language(SupportedLocale.fromCode(inkflowProperties.getDefaultLanguage()))
                .accountType(accountType)
                .companySize(request.getCompanySize())
                .isActive(true)
                .build();
        tenant = tenantRepository.save(tenant);
        log.info("Created tenant: {}", tenant.getId());

        Staff owner = Staff.builder()
                .authUserId(supabaseUserId.toString())
                .tenantId(tenant.getId())
                .email(email)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(UserRole.OWNER)
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .calendarColor("#6366f1")
                .build();
        owner = staffRepository.save(owner);
        log.info("Created owner staff: {}", owner.getId());

        Location defaultLocation = Location.builder()
                .tenantId(tenant.getId())
                .name("Основна студія")
                .address("")
                .phone("")
                .color("#6366f1")
                .isActive(true)
                .build();
        locationRepository.save(defaultLocation);

        builtInTemplateSeeder.seedDefaultsForTenant(tenant.getId());
        subscriptionService.createTrialForTenant(tenant.getId());

        return OnboardingResponse.builder()
                .userId(owner.getId())
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .role(UserRole.OWNER.getValue())
                .success(true)
                .build();
    }
}
