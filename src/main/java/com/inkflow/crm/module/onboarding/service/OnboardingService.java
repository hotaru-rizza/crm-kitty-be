package com.inkflow.crm.module.onboarding.service;

import com.inkflow.crm.config.BypassTenantFilter;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.AccountType;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.SupportedCurrency;
import com.inkflow.crm.domain.enums.SupportedLocale;
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
import com.inkflow.crm.module.onboarding.support.OnboardingDefaults;
import com.inkflow.crm.module.service.support.ServiceDurationPolicy;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@BypassTenantFilter
public class OnboardingService {

    private static final String ONBOARDING_LOCK_QUERY =
            "SELECT pg_advisory_xact_lock(hashtext(:lockKey))";

    private final TenantRepository tenantRepository;
    private final StaffRepository staffRepository;
    private final LocationRepository locationRepository;
    private final ServiceRepository serviceRepository;
    private final BuiltInTemplateSeeder builtInTemplateSeeder;
    private final CategoryConfigService categoryConfigService;
    private final SubscriptionService subscriptionService;
    private final SupabaseAdminService supabaseAdminService;
    private final InkflowProperties inkflowProperties;
    private final EntityManager entityManager;

    @Transactional
    public OnboardingResponse completeOnboarding(UUID supabaseUserId, String email, OnboardingRequest request) {
        acquireOnboardingLock(supabaseUserId);

        return staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString())
                .map(this::toExistingResponse)
                .orElseGet(() -> createNewTenantSafely(supabaseUserId, email, request));
    }

    private OnboardingResponse createNewTenantSafely(UUID supabaseUserId, String email, OnboardingRequest request) {
        try {
            return createNewTenant(supabaseUserId, email, request);
        } catch (DataIntegrityViolationException ex) {
            return staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId.toString())
                    .map(this::toExistingResponse)
                    .orElseThrow(() -> ex);
        }
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

        Tenant tenant = createTenant(request);
        Staff owner = createOwner(supabaseUserId, email, request, tenant.getId());
        Location defaultLocation = createDefaultLocation(tenant.getId(), request);
        locationRepository.save(defaultLocation);
        log.info("Created default location for tenant {}: city={}", tenant.getId(), defaultLocation.getCity());

        createInitialServiceIfPresent(tenant.getId(), request.getService());

        builtInTemplateSeeder.seedDefaultsForTenant(tenant.getId());
        categoryConfigService.ensureDefaults(tenant.getId());
        subscriptionService.createTrialForTenant(tenant.getId());
        supabaseAdminService.syncUserTenantClaims(
                supabaseUserId.toString(),
                tenant.getId(),
                UserRole.OWNER.getValue()
        );

        return OnboardingResponse.builder()
                .userId(owner.getId())
                .tenantId(tenant.getId())
                .tenantName(tenant.getName())
                .role(UserRole.OWNER.getValue())
                .success(true)
                .build();
    }

    private Tenant createTenant(OnboardingRequest request) {
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
        return tenant;
    }

    private Staff createOwner(UUID supabaseUserId, String email, OnboardingRequest request, UUID tenantId) {
        Staff owner = Staff.builder()
                .authUserId(supabaseUserId.toString())
                .tenantId(tenantId)
                .email(email)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(normalizeOptionalText(request.getPhone()))
                .instagram(normalizeOptionalText(request.getInstagram()))
                .role(UserRole.OWNER)
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .calendarColor(OnboardingDefaults.DEFAULT_LOCATION_COLOR)
                .termsAcceptedAt(request.getAcceptedTermsAt())
                .termsVersion(request.getTermsVersion())
                .build();
        owner = staffRepository.save(owner);
        log.info("Created owner staff: {}", owner.getId());
        return owner;
    }

    private Location createDefaultLocation(UUID tenantId, OnboardingRequest request) {
        String city = normalizeOptionalText(request.getCity());
        String instagram = normalizeOptionalText(request.getInstagram());

        return Location.builder()
                .tenantId(tenantId)
                .name(request.getCompanyName())
                .city(city)
                .address("")
                .instagram(instagram)
                .phone("")
                .color(OnboardingDefaults.DEFAULT_LOCATION_COLOR)
                .isActive(true)
                .isDefault(true)
                .build();
    }

    private void createInitialServiceIfPresent(UUID tenantId, OnboardingServiceDraftDto serviceDraft) {
        if (serviceDraft == null || !StringUtils.hasText(serviceDraft.getTitle())) {
            return;
        }

        PricingType pricingType = resolvePricingType(serviceDraft.getPricingType());

        Service service = Service.builder()
                .tenantId(tenantId)
                .title(serviceDraft.getTitle().trim())
                .pricingType(pricingType)
                .price(serviceDraft.getPrice())
                .duration(ServiceDurationPolicy.resolveForCreate(pricingType, serviceDraft.getDuration()))
                .color(OnboardingDefaults.DEFAULT_SERVICE_COLOR)
                .isActive(true)
                .build();
        serviceRepository.save(service);
        log.info("Created initial service for tenant {}: title={}, pricingType={}",
                tenantId, service.getTitle(), pricingType);
    }

    private PricingType resolvePricingType(String pricingType) {
        if (!StringUtils.hasText(pricingType)) {
            return OnboardingDefaults.DEFAULT_SERVICE_PRICING_TYPE;
        }
        return PricingType.fromValue(pricingType.trim());
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void acquireOnboardingLock(UUID supabaseUserId) {
        entityManager.createNativeQuery(ONBOARDING_LOCK_QUERY)
                .setParameter("lockKey", supabaseUserId.toString())
                .getSingleResult();
    }
}
