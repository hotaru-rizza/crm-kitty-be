package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffFaq;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.repository.StaffFaqRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.catalog.dto.PublicArtistDto;
import com.inkflow.crm.module.catalog.support.PortfolioShowcaseResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicArtistService {

    private final StaffRepository staffRepository;
    private final StaffFaqRepository staffFaqRepository;
    private final PortfolioShowcaseResolver showcaseResolver;
    private final InkflowProperties inkflowProperties;

    @Transactional(readOnly = true)
    public List<PublicArtistDto> findAll(String city, String style, String search) {
        List<Staff> artists = staffRepository.findAllPublicArtists().stream()
                .filter(staff -> matchesCity(staff, city))
                .filter(staff -> matchesStyle(staff, style))
                .filter(staff -> matchesSearch(staff, search))
                .toList();

        if (artists.isEmpty()) {
            return List.of();
        }

        List<UUID> staffIds = artists.stream().map(Staff::getId).toList();
        Map<UUID, List<String>> showcaseUrls = showcaseResolver.resolveUrlsBatch(staffIds);
        Map<UUID, List<PublicArtistDto.FaqEntry>> faqByStaff = loadFaqEntries(staffIds);

        return artists.stream()
                .map(staff -> toDto(staff, showcaseUrls, faqByStaff))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PublicArtistDto> findById(UUID id) {
        return staffRepository.findPublicArtistById(id)
                .map(staff -> toDto(
                        staff,
                        showcaseResolver.resolveUrlsBatch(List.of(staff.getId())),
                        loadFaqEntries(List.of(staff.getId()))
                ));
    }

    private Map<UUID, List<PublicArtistDto.FaqEntry>> loadFaqEntries(List<UUID> staffIds) {
        return staffFaqRepository.findByStaffIdInOrderByStaffIdAscSortOrderAsc(staffIds).stream()
                .collect(Collectors.groupingBy(
                        StaffFaq::getStaffId,
                        Collectors.mapping(
                                faq -> new PublicArtistDto.FaqEntry(faq.getQuestion(), faq.getAnswer()),
                                Collectors.toList()
                        )
                ));
    }

    private boolean matchesCity(Staff staff, String city) {
        if (city == null || city.isBlank()) return true;
        return staff.getLocations().stream()
                .anyMatch(loc -> loc.getAddress() != null &&
                        loc.getAddress().toLowerCase().contains(city.toLowerCase()));
    }

    private boolean matchesStyle(Staff staff, String style) {
        if (style == null || style.isBlank()) return true;
        return staff.getSpecialization().stream()
                .anyMatch(s -> s.equalsIgnoreCase(style));
    }

    private boolean matchesSearch(Staff staff, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.toLowerCase();
        String fullName = staff.getFullName().toLowerCase();
        boolean nameMatch = fullName.contains(q);
        boolean studioMatch = staff.getLocations().stream()
                .anyMatch(loc -> (loc.getName() != null && loc.getName().toLowerCase().contains(q))
                        || (loc.getAddress() != null && loc.getAddress().toLowerCase().contains(q)));
        return nameMatch || studioMatch;
    }

    private PublicArtistDto toDto(
            Staff staff,
            Map<UUID, List<String>> showcaseUrls,
            Map<UUID, List<PublicArtistDto.FaqEntry>> faqByStaff) {
        Location primaryLocation = staff.getLocations().stream().findFirst().orElse(null);

        String studioName = primaryLocation != null ? primaryLocation.getName() : null;
        String studioAddress = primaryLocation != null ? primaryLocation.getAddress() : null;
        String studioPhoto = primaryLocation != null ? primaryLocation.getPhotoUrl() : null;
        Double lat = primaryLocation != null ? primaryLocation.getLatitude() : null;
        Double lng = primaryLocation != null ? primaryLocation.getLongitude() : null;

        List<PublicArtistDto.ScheduleEntry> schedule = staff.getSchedules().stream()
                .sorted((a, b) -> Integer.compare(a.getDayOfWeek().getOrder(), b.getDayOfWeek().getOrder()))
                .map(this::toScheduleEntry)
                .distinct()
                .toList();

        ZoneId zone = inkflowProperties.defaultZoneId();
        boolean isOpen = isCurrentlyOpen(staff.getSchedules(), zone);

        int experience = staff.getCreatedAt() != null
                ? (int) ChronoUnit.YEARS.between(
                        staff.getCreatedAt().atZone(zone).toLocalDate(), LocalDate.now(zone))
                : 0;

        String instagram = staff.getInstagram() != null && !staff.getInstagram().isBlank()
                ? staff.getInstagram()
                : (primaryLocation != null ? primaryLocation.getInstagram() : null);

        List<PublicArtistDto.FaqEntry> faqEntries = faqByStaff.getOrDefault(staff.getId(), List.of());
        List<String> showcase = showcaseUrls.getOrDefault(staff.getId(), List.of());

        return new PublicArtistDto(
                staff.getId(),
                staff.getFullName(),
                staff.getAvatar(),
                staff.getBio(),
                Math.max(experience, 1),
                staff.getHourlyRate(),
                studioName,
                studioAddress,
                studioPhoto,
                lat,
                lng,
                List.copyOf(staff.getSpecialization()),
                List.copyOf(staff.getDontDoList()),
                instagram,
                isOpen,
                0,
                showcase,
                schedule,
                faqEntries,
                Collections.emptyList()
        );
    }

    private PublicArtistDto.ScheduleEntry toScheduleEntry(StaffSchedule s) {
        if (!s.getIsWorking()) {
            return new PublicArtistDto.ScheduleEntry(s.getDayOfWeek().getValue(), "Вихідний");
        }
        String hours = formatTime(s.getStartTime()) + " - " + formatTime(s.getEndTime());
        return new PublicArtistDto.ScheduleEntry(s.getDayOfWeek().getValue(), hours);
    }

    private String formatTime(LocalTime time) {
        return time != null ? String.format("%02d:%02d", time.getHour(), time.getMinute()) : "—";
    }

    private boolean isCurrentlyOpen(java.util.Collection<StaffSchedule> schedules, ZoneId zone) {
        java.time.DayOfWeek today = LocalDate.now(zone).getDayOfWeek();
        LocalTime now = LocalTime.now(zone);
        return schedules.stream()
                .filter(s -> s.getDayOfWeek().name().equals(today.name()))
                .anyMatch(s -> s.isWorkingAt(now));
    }
}
