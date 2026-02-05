package com.inkflow.crm.module.booking.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.enums.*;
import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.booking.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicBookingService {

    private final TenantRepository tenantRepository;
    private final StaffRepository staffRepository;
    private final ServiceRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final LocationRepository locationRepository;
    private final RequestRepository requestRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    private static final ZoneId UKRAINE_ZONE = ZoneId.of("Europe/Kiev");

    @Transactional(readOnly = true)
    public PublicSalonDto getSalonBySubdomain(String subdomain) {
        Tenant tenant = tenantRepository.findBySubdomain(subdomain)
                .orElseThrow(() -> new ResourceNotFoundException(
                        com.inkflow.crm.common.exception.ErrorCode.NOT_FOUND,
                        "Salon not found: " + subdomain));

        if (!tenant.getIsActive()) {
            throw new BusinessRuleException("This salon is currently not accepting bookings");
        }

        CompanySettings settings = companySettingsRepository.findByTenantId(tenant.getId())
                .orElse(null);

        // Count artists and services
        long artistsCount = staffRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
                tenant.getId(), StaffStatus.WORKING);
        long servicesCount = serviceRepository.countByTenantIdAndIsActiveTrueAndDeletedAtIsNull(
                tenant.getId());

        return PublicSalonDto.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .subdomain(tenant.getSubdomain())
                .description(null) // TODO: Add to Tenant entity
                .logoUrl(null)     // TODO: Add to Tenant entity
                .coverUrl(null)    // TODO: Add to Tenant entity
                .phone(null)       // TODO: Get from settings or location
                .email(null)
                .instagram(null)
                .address(null)
                .city(null)
                .workingHoursStart(settings != null ? settings.getWorkingHoursStart().toString() : "10:00")
                .workingHoursEnd(settings != null ? settings.getWorkingHoursEnd().toString() : "20:00")
                .allowOnlineBooking(settings != null ? settings.getAllowOnlineBooking() : true)
                .minAdvanceHours(settings != null ? settings.getMinAdvanceHours() : 24)
                .maxAdvanceDays(settings != null ? settings.getMaxAdvanceDays() : 60)
                .artistsCount((int) artistsCount)
                .servicesCount((int) servicesCount)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PublicArtistDto> getArtistsBySubdomain(String subdomain) {
        Tenant tenant = getTenantBySubdomain(subdomain);

        List<Staff> artists = staffRepository.findByTenantIdAndStatusAndDeletedAtIsNull(
                tenant.getId(), StaffStatus.WORKING);

        return artists.stream()
                .filter(artist -> artist.getRole() != UserRole.ADMIN) // Only show artists, not admins
                .map(this::mapToPublicArtistDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PublicArtistDto getArtistById(String subdomain, UUID artistId) {
        Tenant tenant = getTenantBySubdomain(subdomain);

        Staff artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(artistId, tenant.getId())
                .orElseThrow(() -> ResourceNotFoundException.staff(artistId.toString()));

        return mapToPublicArtistDto(artist);
    }

    @Transactional(readOnly = true)
    public List<PublicServiceDto> getServicesBySubdomain(String subdomain) {
        Tenant tenant = getTenantBySubdomain(subdomain);

        List<com.inkflow.crm.domain.entity.Service> services = 
                serviceRepository.findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(tenant.getId());

        return services.stream()
                .map(this::mapToPublicServiceDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PublicServiceDto> getArtistServices(String subdomain, UUID artistId) {
        Tenant tenant = getTenantBySubdomain(subdomain);

        Staff artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(artistId, tenant.getId())
                .orElseThrow(() -> ResourceNotFoundException.staff(artistId.toString()));

        // Get all active services for this tenant
        // In future, can filter by artist-specific services
        List<com.inkflow.crm.domain.entity.Service> services = 
                serviceRepository.findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(tenant.getId());

        return services.stream()
                .map(service -> mapToPublicServiceDtoForArtist(service, artist))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TimeSlotDto> getAvailableSlots(String subdomain, UUID artistId, UUID serviceId,
                                                LocalDate fromDate, LocalDate toDate) {
        Tenant tenant = getTenantBySubdomain(subdomain);

        Staff artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(artistId, tenant.getId())
                .orElseThrow(() -> ResourceNotFoundException.staff(artistId.toString()));

        com.inkflow.crm.domain.entity.Service service = 
                serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(serviceId, tenant.getId())
                .orElseThrow(() -> ResourceNotFoundException.service(serviceId.toString()));

        CompanySettings settings = companySettingsRepository.findByTenantId(tenant.getId())
                .orElse(null);

        // Validate date range
        LocalDate today = LocalDate.now(UKRAINE_ZONE);
        int minAdvanceHours = settings != null ? settings.getMinAdvanceHours() : 24;
        int maxAdvanceDays = settings != null ? settings.getMaxAdvanceDays() : 60;

        LocalDate minDate = today.plusDays(minAdvanceHours / 24);
        LocalDate maxDate = today.plusDays(maxAdvanceDays);

        if (fromDate.isBefore(minDate)) fromDate = minDate;
        if (toDate.isAfter(maxDate)) toDate = maxDate;

        // Get artist schedule
        List<StaffSchedule> schedules = artist.getSchedules();
        Map<com.inkflow.crm.domain.enums.DayOfWeek, StaffSchedule> scheduleMap = schedules.stream()
                .collect(Collectors.toMap(StaffSchedule::getDayOfWeek, s -> s));

        // Get existing appointments for artist in date range
        Instant fromInstant = fromDate.atStartOfDay(UKRAINE_ZONE).toInstant();
        Instant toInstant = toDate.plusDays(1).atStartOfDay(UKRAINE_ZONE).toInstant();
        
        List<Appointment> existingAppointments = appointmentRepository
                .findByArtistIdAndStartTimeBetweenAndStatusNotAndDeletedAtIsNull(
                        artistId, fromInstant, toInstant, AppointmentStatus.CANCELLED);

        // Get approved leaves for the date range
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findOverlappingLeaves(
                tenant.getId(), artistId, fromDate, toDate);

        // Build slots for each day
        List<TimeSlotDto> result = new ArrayList<>();
        int serviceDuration = service.getDuration();
        LocalTime defaultStart = settings != null ? settings.getWorkingHoursStart() : LocalTime.of(10, 0);
        LocalTime defaultEnd = settings != null ? settings.getWorkingHoursEnd() : LocalTime.of(20, 0);

        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            java.time.DayOfWeek javaDow = date.getDayOfWeek();
            com.inkflow.crm.domain.enums.DayOfWeek dow = com.inkflow.crm.domain.enums.DayOfWeek.fromJavaDayOfWeek(javaDow);
            StaffSchedule daySchedule = scheduleMap.get(dow);

            // Check if artist is on approved leave this day
            final LocalDate checkDate = date;
            boolean isOnLeave = approvedLeaves.stream()
                    .anyMatch(leave -> leave.coversDate(checkDate));

            boolean isDayAvailable = daySchedule != null && daySchedule.getIsWorking() && !isOnLeave;

            TimeSlotDto daySlots = TimeSlotDto.builder()
                    .date(date)
                    .dayOfWeek(javaDow.name())
                    .dayName(javaDow.getDisplayName(TextStyle.FULL, new Locale("uk")))
                    .isAvailable(isDayAvailable)
                    .slots(new ArrayList<>())
                    .build();

            if (isDayAvailable) {
                LocalTime start = daySchedule.getStartTime() != null ? daySchedule.getStartTime() : defaultStart;
                LocalTime end = daySchedule.getEndTime() != null ? daySchedule.getEndTime() : defaultEnd;

                // Generate slots
                LocalTime slotStart = start;
                final LocalDate currentDate = date;
                
                while (slotStart.plusMinutes(serviceDuration).compareTo(end) <= 0) {
                    LocalTime slotEnd = slotStart.plusMinutes(serviceDuration);

                    // Check if slot is available (no overlapping appointments)
                    final LocalTime checkStart = slotStart;
                    final LocalTime checkEnd = slotEnd;
                    
                    boolean isBooked = existingAppointments.stream()
                            .anyMatch(apt -> {
                                LocalDateTime aptStart = apt.getStartTime().atZone(UKRAINE_ZONE).toLocalDateTime();
                                LocalDateTime aptEnd = apt.getEndTime().atZone(UKRAINE_ZONE).toLocalDateTime();
                                
                                if (!aptStart.toLocalDate().equals(currentDate)) return false;
                                
                                LocalTime aptStartTime = aptStart.toLocalTime();
                                LocalTime aptEndTime = aptEnd.toLocalTime();
                                
                                // Check overlap
                                return checkStart.isBefore(aptEndTime) && checkEnd.isAfter(aptStartTime);
                            });

                    // Check if slot is in the past
                    boolean isInPast = false;
                    if (currentDate.equals(today)) {
                        LocalTime now = LocalTime.now(UKRAINE_ZONE);
                        isInPast = slotStart.isBefore(now.plusHours(minAdvanceHours % 24));
                    }

                    daySlots.getSlots().add(TimeSlotDto.Slot.builder()
                            .startTime(slotStart)
                            .endTime(slotEnd)
                            .startTimeFormatted(slotStart.format(DateTimeFormatter.ofPattern("HH:mm")))
                            .endTimeFormatted(slotEnd.format(DateTimeFormatter.ofPattern("HH:mm")))
                            .isAvailable(!isBooked && !isInPast)
                            .build());

                    slotStart = slotStart.plusMinutes(30); // 30-minute intervals
                }
            }

            result.add(daySlots);
        }

        return result;
    }

    @Transactional
    public UUID createBooking(String subdomain, CreateBookingRequest request) {
        Tenant tenant = getTenantBySubdomain(subdomain);

        // Validate artist
        Staff artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getArtistId(), tenant.getId())
                .orElseThrow(() -> ResourceNotFoundException.staff(request.getArtistId().toString()));

        // Validate service
        com.inkflow.crm.domain.entity.Service service = 
                serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getServiceId(), tenant.getId())
                .orElseThrow(() -> ResourceNotFoundException.service(request.getServiceId().toString()));

        // Get location (use first active location)
        Location location = locationRepository.findFirstByTenantIdAndIsActiveTrueAndDeletedAtIsNull(tenant.getId())
                .orElseThrow(() -> new BusinessRuleException("No active location found"));

        // Check if slot is still available
        LocalDateTime startDateTime = LocalDateTime.of(request.getDate(), request.getStartTime());
        LocalDateTime endDateTime = startDateTime.plusMinutes(service.getDuration());

        Instant startInstant = startDateTime.atZone(UKRAINE_ZONE).toInstant();
        Instant endInstant = endDateTime.atZone(UKRAINE_ZONE).toInstant();

        boolean hasConflict = appointmentRepository.existsConflictingAppointment(
                artist.getId(), startInstant, endInstant);

        if (hasConflict) {
            throw new BusinessRuleException("This time slot is no longer available. Please choose another time.");
        }

        // Create a Request (not Appointment) - to be confirmed by salon
        Request bookingRequest = Request.builder()
                .tenantId(tenant.getId())
                .source(RequestSource.WEBSITE)
                .clientName(request.getClientName())
                .clientNickname(request.getInstagram())
                .phone(request.getPhone())
                .instagram(request.getInstagram())
                .message(buildBookingMessage(request, artist, service, startDateTime))
                .status(RequestStatus.NEW)
                .location(location)
                .build();

        bookingRequest = requestRepository.save(bookingRequest);

        log.info("New booking request created: {} for salon {} from {}", 
                bookingRequest.getId(), tenant.getName(), request.getClientName());

        return bookingRequest.getId();
    }

    private String buildBookingMessage(CreateBookingRequest request, Staff artist, 
                                       com.inkflow.crm.domain.entity.Service service,
                                       LocalDateTime dateTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("🗓 Онлайн-запис\n\n");
        sb.append("Майстер: ").append(artist.getFullName()).append("\n");
        sb.append("Послуга: ").append(service.getTitle()).append("\n");
        sb.append("Дата: ").append(dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append("\n");
        sb.append("Час: ").append(dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))).append("\n");
        
        if (request.getMessage() != null && !request.getMessage().isEmpty()) {
            sb.append("\nПобажання: ").append(request.getMessage());
        }
        
        return sb.toString();
    }

    private Tenant getTenantBySubdomain(String subdomain) {
        return tenantRepository.findBySubdomain(subdomain)
                .orElseThrow(() -> new ResourceNotFoundException(
                        com.inkflow.crm.common.exception.ErrorCode.NOT_FOUND,
                        "Salon not found: " + subdomain));
    }

    private PublicArtistDto mapToPublicArtistDto(Staff artist) {
        List<PublicArtistDto.ScheduleDayDto> schedule = new ArrayList<>();
        
        if (artist.getSchedules() != null) {
            for (StaffSchedule s : artist.getSchedules()) {
                schedule.add(PublicArtistDto.ScheduleDayDto.builder()
                        .dayOfWeek(s.getDayOfWeek().getValue())
                        .dayName(s.getDayOfWeek().getDisplayName())
                        .isWorking(s.getIsWorking())
                        .startTime(s.getStartTime() != null ? s.getStartTime().toString() : null)
                        .endTime(s.getEndTime() != null ? s.getEndTime().toString() : null)
                        .build());
            }
        }

        return PublicArtistDto.builder()
                .id(artist.getId())
                .firstName(artist.getFirstName())
                .lastName(artist.getLastName())
                .fullName(artist.getFullName())
                .avatarUrl(artist.getAvatar())
                .bio(artist.getBio())
                .specialization(artist.getSpecialization())
                .instagram(artist.getInstagram())
                .calendarColor(artist.getCalendarColor())
                .portfolioImages(new ArrayList<>()) // TODO: Get from gallery
                .services(new ArrayList<>()) // Will be fetched separately
                .schedule(schedule)
                .build();
    }

    private PublicServiceDto mapToPublicServiceDto(com.inkflow.crm.domain.entity.Service service) {
        return PublicServiceDto.builder()
                .id(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .pricingType(service.getPricingType().getValue())
                .price(service.getPrice())
                .priceFrom(service.getPrice()) // TODO: Add price range support
                .priceTo(null)
                .duration(service.getDuration())
                .color(service.getColor())
                .build();
    }

    private PublicServiceDto mapToPublicServiceDtoForArtist(
            com.inkflow.crm.domain.entity.Service service, Staff artist) {
        // Check for artist-specific pricing
        BigDecimal price = service.getPrice();
        
        if (service.getArtistPricings() != null) {
            Optional<ArtistServicePricing> artistPricing = service.getArtistPricings().stream()
                    .filter(p -> p.getStaff().getId().equals(artist.getId()))
                    .findFirst();
            
            if (artistPricing.isPresent()) {
                price = artistPricing.get().getPrice();
            }
        }

        return PublicServiceDto.builder()
                .id(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .pricingType(service.getPricingType().getValue())
                .price(price)
                .priceFrom(price)
                .priceTo(null)
                .duration(service.getDuration())
                .color(service.getColor())
                .build();
    }
}
