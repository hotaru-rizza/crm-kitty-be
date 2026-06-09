package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.DayOfWeek;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.infrastructure.supabase.SupabaseAdminService;
import com.inkflow.crm.module.location.dto.LocationDto;
import com.inkflow.crm.module.staff.dto.*;
import com.inkflow.crm.module.staff.mapper.StaffMapper;
import com.inkflow.crm.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final StaffScheduleRepository staffScheduleRepository;
    private final StaffInviteRepository staffInviteRepository;
    private final LocationRepository locationRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceRepository serviceRepository;
    private final ArtistServicePricingRepository artistServicePricingRepository;
    private final StaffMapper staffMapper;
    private final EntityManager entityManager;
    private final StaffFaqRepository staffFaqRepository;
    private final SupabaseAdminService supabaseAdminService;

    @Transactional(readOnly = true)
    public PageResult<StaffDto> getAllStaff(PageRequest pageRequest, String search, String role, UUID locationId, String accountStatus) {
        Page<Staff> page = getStaffPage(pageRequest, search, role, locationId, accountStatus);
        List<StaffDto> data = staffMapper.toDtoList(page.getContent());
        return new PageResult<>(data, PaginationDto.from(page));
    }

    private Page<Staff> getStaffPage(PageRequest pageRequest, String search, String role, UUID locationId, String accountStatus) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        com.inkflow.crm.domain.enums.UserRole userRole = role != null
            ? com.inkflow.crm.domain.enums.UserRole.fromValue(role)
            : null;
        com.inkflow.crm.domain.enums.AccountStatus accStatus = accountStatus != null
            ? com.inkflow.crm.domain.enums.AccountStatus.fromValue(accountStatus)
            : null;
        return staffRepository.findWithFilters(tenantId, search, userRole, locationId, accStatus, pageRequest.toPageable());
    }

    @Transactional(readOnly = true)
    public StaffDetailDto getStaffById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(id.toString()));
        return buildDetailDto(staff);
    }

    @Transactional
    public StaffDto createStaff(CreateStaffRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        if (staffRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(request.getEmail(), tenantId)) {
            throw BusinessRuleException.emailAlreadyExists(request.getEmail());
        }

        Staff staff = staffMapper.toEntity(request);
        staff.setTenantId(tenantId);

        Set<Location> locations = new HashSet<>(locationRepository.findAllById(request.getLocationIds()));
        staff.setLocations(locations);

        staff = staffRepository.save(staff);
        return staffMapper.toDto(staff);
    }

    @Transactional
    public StaffDto updateStaff(UUID id, UpdateStaffRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(id.toString()));

        if (request.getEmail() != null && !request.getEmail().equals(staff.getEmail())) {
            if (staffRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(request.getEmail(), tenantId)) {
                throw BusinessRuleException.emailAlreadyExists(request.getEmail());
            }
        }

        staffMapper.updateEntity(request, staff);

        if (request.getLocationIds() != null) {
            Set<Location> locations = new HashSet<>(locationRepository.findAllById(request.getLocationIds()));
            staff.setLocations(locations);
        }

        staff = staffRepository.save(staff);
        return staffMapper.toDto(staff);
    }

    @Transactional
    public void deleteStaff(UUID id) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(id.toString()));

        staff.softDelete();
        staffRepository.save(staff);
    }

    @Transactional
    public void updateSchedule(UUID staffId, UpdateScheduleRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        // Clear existing schedules and flush to execute DELETEs first
        staff.getSchedules().clear();
        entityManager.flush();

        // Create and add new schedules
        request.getSchedule().forEach(entry -> {
            StaffSchedule schedule = StaffSchedule.builder()
                    .staff(staff)
                    .dayOfWeek(DayOfWeek.fromValue(entry.getDayOfWeek()))
                    .isWorking(entry.getIsWorking())
                    .startTime(entry.getStartTime() != null ? LocalTime.parse(entry.getStartTime()) : null)
                    .endTime(entry.getEndTime() != null ? LocalTime.parse(entry.getEndTime()) : null)
                    .build();
            staff.getSchedules().add(schedule);
        });

        staffRepository.save(staff);
    }

    @Transactional(readOnly = true)
    public InviteInfoDto getInviteInfo(String token) {
        StaffInvite invite = staffInviteRepository.findByToken(token)
                .orElseThrow(() -> new BusinessRuleException("Invalid invite token"));

        return InviteInfoDto.builder()
                .email(invite.getEmail())
                .role(invite.getRole().getValue())
                .expiresAt(invite.getExpiresAt())
                .expired(invite.isExpired())
                .accepted(invite.isAccepted())
                .build();
    }

    @Transactional
    public String inviteStaff(InviteStaffRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if (staffRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(request.getEmail(), tenantId)) {
            throw BusinessRuleException.emailAlreadyExists(request.getEmail());
        }

        if (staffInviteRepository.existsByEmailAndTenantIdAndAcceptedAtIsNull(request.getEmail(), tenantId)) {
            throw new BusinessRuleException("Invite already pending for this email");
        }

        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(7));

        StaffInvite invite = StaffInvite.builder()
                .tenantId(tenantId)
                .email(request.getEmail())
                .role(com.inkflow.crm.domain.enums.UserRole.fromValue(request.getRole()))
                .calendarColor(request.getCalendarColor())
                .isServiceProvider(request.getIsServiceProvider())
                .token(token)
                .expiresAt(expiresAt)
                .invitedBy(currentUserId)
                .locationIds(new HashSet<>(request.getLocationIds()))
                .build();

        staffInviteRepository.save(invite);
        return token;
    }

    @Transactional
    public StaffDto acceptInvite(AcceptInviteRequest request) {
        StaffInvite invite = staffInviteRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BusinessRuleException("Invalid invite token"));

        if (invite.isExpired()) {
            throw new BusinessRuleException("Invite has expired");
        }

        if (invite.isAccepted()) {
            throw new BusinessRuleException("Invite has already been accepted");
        }

        Staff staff = Staff.builder()
                .tenantId(invite.getTenantId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(invite.getEmail())
                .phone(request.getPhone())
                .role(invite.getRole())
                .calendarColor(invite.getCalendarColor())
                .isServiceProvider(invite.getIsServiceProvider())
                .authUserId(request.getAuthUserId())
                .status(com.inkflow.crm.domain.enums.StaffStatus.WORKING)
                .build();

        Set<Location> locations = new HashSet<>(locationRepository.findAllById(invite.getLocationIds()));
        staff.setLocations(locations);

        staff = staffRepository.save(staff);

        invite.setAcceptedAt(Instant.now());
        staffInviteRepository.save(invite);

        return staffMapper.toDto(staff);
    }

    private StaffDetailDto buildDetailDto(Staff staff) {
        List<LocationDto> locations = staff.getLocations().stream()
                .filter(l -> l.getDeletedAt() == null)
                .map(l -> LocationDto.builder()
                        .id(l.getId())
                        .name(l.getName())
                        .address(l.getAddress())
                        .color(l.getColor())
                        .isActive(l.getIsActive())
                        .build())
                .collect(Collectors.toList());

        List<StaffDetailDto.ScheduleDto> schedule = staff.getSchedules().stream()
                .map(s -> StaffDetailDto.ScheduleDto.builder()
                        .dayOfWeek(s.getDayOfWeek().getValue())
                        .isWorking(s.getIsWorking())
                        .startTime(s.getStartTime() != null ? s.getStartTime().toString() : null)
                        .endTime(s.getEndTime() != null ? s.getEndTime().toString() : null)
                        .build())
                .collect(Collectors.toList());

        StaffDetailDto.StaffStatsDto stats = calculateStats(staff);

        return StaffDetailDto.builder()
                .id(staff.getId())
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .email(staff.getEmail())
                .phone(staff.getPhone())
                .avatar(staff.getAvatar())
                .role(staff.getRole().getValue())
                .calendarColor(staff.getCalendarColor())
                .specialization(new ArrayList<>(staff.getSpecialization()))
                .portfolioImages(new ArrayList<>(staff.getPortfolioImages()))
                .bio(staff.getBio())
                .status(staff.getStatus().getValue())
                .accountStatus(staff.getAccountStatus().getValue())
                .locations(locations)
                .schedule(schedule)
                .stats(stats)
                .salaryType(staff.getSalaryType() != null ? staff.getSalaryType().getValue() : "none")
                .salaryRate(staff.getSalaryRate())
                .bankDetails(staff.getBankDetails())
                .isServiceProvider(staff.getIsServiceProvider())
                .instagram(staff.getInstagram())
                .hourlyRate(staff.getHourlyRate())
                .dontDoList(new ArrayList<>(staff.getDontDoList()))
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .build();
    }

    private StaffDetailDto.StaffStatsDto calculateStats(Staff staff) {
        UUID tenantId = staff.getTenantId();
        LocalDate now = LocalDate.now();
        Instant monthStart = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant monthEnd = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<Appointment> monthAppointments = appointmentRepository.findByTenantIdAndArtistIdAndDateRange(
                tenantId, staff.getId(), monthStart, monthEnd);

        List<Appointment> upcoming = appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                staff.getId(), List.of(AppointmentStatus.NEW, AppointmentStatus.CONFIRMED), Instant.now());

        return StaffDetailDto.StaffStatsDto.builder()
                .appointmentsThisMonth(monthAppointments.size())
                .upcomingAppointments(upcoming.size())
                .build();
    }

    @Transactional(readOnly = true)
    public List<StaffServiceDto> getStaffServices(UUID staffId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        List<ArtistServicePricing> pricings = artistServicePricingRepository.findByStaffId(staffId);

        return pricings.stream()
                .filter(p -> p.getService().getDeletedAt() == null)
                .map(this::mapToStaffServiceDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<StaffServiceDto> updateStaffServices(UUID staffId, UpdateStaffServicesRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        // Delete all existing service assignments
        artistServicePricingRepository.deleteByStaffId(staffId);
        entityManager.flush();

        // Create new assignments
        List<ArtistServicePricing> newPricings = new ArrayList<>();
        for (UpdateStaffServicesRequest.ServiceAssignment assignment : request.getServices()) {
            com.inkflow.crm.domain.entity.Service service = serviceRepository
                    .findByIdAndTenantIdAndDeletedAtIsNull(assignment.getServiceId(), tenantId)
                    .orElseThrow(() -> ResourceNotFoundException.service(assignment.getServiceId().toString()));

            ArtistServicePricing pricing = ArtistServicePricing.builder()
                    .staff(staff)
                    .service(service)
                    .price(assignment.getCustomPrice() != null ? assignment.getCustomPrice() : service.getPrice())
                    .duration(assignment.getCustomDuration() != null ? assignment.getCustomDuration() : service.getDuration())
                    .build();

            newPricings.add(artistServicePricingRepository.save(pricing));
        }

        return newPricings.stream()
                .map(this::mapToStaffServiceDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public StaffServiceDto addServiceToStaff(UUID staffId, UUID serviceId, java.math.BigDecimal customPrice, Integer customDuration) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        com.inkflow.crm.domain.entity.Service service = serviceRepository
                .findByIdAndTenantIdAndDeletedAtIsNull(serviceId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.service(serviceId.toString()));

        // Check if already assigned
        Optional<ArtistServicePricing> existing = artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId);
        if (existing.isPresent()) {
            throw new BusinessRuleException("Service is already assigned to this staff member");
        }

        ArtistServicePricing pricing = ArtistServicePricing.builder()
                .staff(staff)
                .service(service)
                .price(customPrice != null ? customPrice : service.getPrice())
                .duration(customDuration != null ? customDuration : service.getDuration())
                .build();

        pricing = artistServicePricingRepository.save(pricing);
        return mapToStaffServiceDto(pricing);
    }

    @Transactional
    public void removeServiceFromStaff(UUID staffId, UUID serviceId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        ArtistServicePricing pricing = artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId)
                .orElseThrow(() -> new BusinessRuleException("Service is not assigned to this staff member"));

        artistServicePricingRepository.delete(pricing);
    }

    @Transactional
    public StaffServiceDto updateStaffServicePricing(UUID staffId, UUID serviceId, java.math.BigDecimal customPrice, Integer customDuration) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        ArtistServicePricing pricing = artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId)
                .orElseThrow(() -> new BusinessRuleException("Service is not assigned to this staff member"));

        if (customPrice != null) {
            pricing.setPrice(customPrice);
        }
        if (customDuration != null) {
            pricing.setDuration(customDuration);
        }

        pricing = artistServicePricingRepository.save(pricing);
        return mapToStaffServiceDto(pricing);
    }

    @Transactional(readOnly = true)
    public int getFutureAppointmentsCount(UUID staffId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));
        List<Appointment> future = appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                staffId,
                List.of(AppointmentStatus.NEW, AppointmentStatus.CONFIRMED),
                Instant.now());
        return future.size();
    }

    @Transactional
    public void reactivateStaff(UUID staffId) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));
        
        if (staff.getAccountStatus() != com.inkflow.crm.domain.enums.AccountStatus.DEACTIVATED) {
            throw new com.inkflow.crm.common.exception.BusinessRuleException(
                    com.inkflow.crm.common.exception.ErrorCode.INVALID_STATUS_TRANSITION,
                    "Staff member is not deactivated");
        }
        staff.setAccountStatus(com.inkflow.crm.domain.enums.AccountStatus.ACTIVE);
        staffRepository.save(staff);
    }

    @Transactional
    public void deactivateStaff(UUID staffId, DeactivateStaffRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Staff staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

        if (staff.getAccountStatus() == com.inkflow.crm.domain.enums.AccountStatus.DEACTIVATED) {
            throw new com.inkflow.crm.common.exception.BusinessRuleException(
                    com.inkflow.crm.common.exception.ErrorCode.STAFF_ALREADY_DEACTIVATED,
                    "Staff member is already deactivated");
        }

        if (request.isCancelFutureAppointments()) {
            List<Appointment> future = appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                    staffId,
                    List.of(AppointmentStatus.NEW, AppointmentStatus.CONFIRMED),
                    Instant.now());
            future.forEach(a -> a.setStatus(AppointmentStatus.CANCELLED));
            appointmentRepository.saveAll(future);
        }

        staff.setAccountStatus(com.inkflow.crm.domain.enums.AccountStatus.DEACTIVATED);
        staff.setAvailableForOnlineBooking(false);
        staffRepository.save(staff);

        if (staff.getAuthUserId() != null) {
            supabaseAdminService.revokeAllSessions(staff.getAuthUserId());
        }
    }

    @Transactional(readOnly = true)
    public List<StaffFaqDto> getFaq(UUID staffId) {
        return staffFaqRepository.findByStaffIdOrderBySortOrderAsc(staffId).stream()
                .map(f -> StaffFaqDto.builder()
                        .id(f.getId())
                        .question(f.getQuestion())
                        .answer(f.getAnswer())
                        .sortOrder(f.getSortOrder())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<StaffFaqDto> upsertFaq(UUID staffId, UpsertFaqRequest request) {
        SecurityUtils.getCurrentTenantId();
        staffFaqRepository.deleteByStaffId(staffId);
        staffFaqRepository.flush();

        List<StaffFaq> saved = new ArrayList<>();
        List<UpsertFaqRequest.FaqItem> items = request.getItems() != null ? request.getItems() : List.of();
        for (int i = 0; i < items.size(); i++) {
            UpsertFaqRequest.FaqItem item = items.get(i);
            StaffFaq faq = StaffFaq.builder()
                    .staffId(staffId)
                    .question(item.getQuestion())
                    .answer(item.getAnswer())
                    .sortOrder(i)
                    .build();
            saved.add(staffFaqRepository.save(faq));
        }

        return saved.stream()
                .map(f -> StaffFaqDto.builder()
                        .id(f.getId())
                        .question(f.getQuestion())
                        .answer(f.getAnswer())
                        .sortOrder(f.getSortOrder())
                        .build())
                .collect(Collectors.toList());
    }

    private StaffServiceDto mapToStaffServiceDto(ArtistServicePricing pricing) {
        com.inkflow.crm.domain.entity.Service service = pricing.getService();
        boolean hasCustomPrice = !pricing.getPrice().equals(service.getPrice());
        boolean hasCustomDuration = pricing.getDuration() != null && !pricing.getDuration().equals(service.getDuration());

        return StaffServiceDto.builder()
                .id(pricing.getId())
                .serviceId(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .pricingType(service.getPricingType().getValue())
                .basePrice(service.getPrice())
                .customPrice(hasCustomPrice ? pricing.getPrice() : null)
                .baseDuration(service.getDuration())
                .customDuration(hasCustomDuration ? pricing.getDuration() : null)
                .color(service.getColor())
                .isActive(service.getIsActive())
                .build();
    }
}
