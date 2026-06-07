package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.catalog.dto.PublicArtistDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicArtistService {

    private final StaffRepository staffRepository;
    private final PortfolioService portfolioService;

    @Transactional(readOnly = true)
    public List<PublicArtistDto> findAll(String city, String style, String search) {
        return staffRepository.findAllPublicArtists().stream()
                .filter(s -> matchesCity(s, city))
                .filter(s -> matchesStyle(s, style))
                .filter(s -> matchesSearch(s, search))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PublicArtistDto> findById(UUID id) {
        return staffRepository.findPublicArtistById(id)
                .map(this::toDto);
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

    private PublicArtistDto toDto(Staff staff) {
        Location primaryLocation = staff.getLocations().stream().findFirst().orElse(null);

        String studioName = primaryLocation != null ? primaryLocation.getName() : null;
        String studioAddress = primaryLocation != null ? primaryLocation.getAddress() : null;
        String studioPhoto = staff.getStudioPhotoUrl() != null
                ? staff.getStudioPhotoUrl()
                : (primaryLocation != null ? primaryLocation.getPhotoUrl() : null);
        Double lat = primaryLocation != null ? primaryLocation.getLatitude() : null;
        Double lng = primaryLocation != null ? primaryLocation.getLongitude() : null;

        List<PublicArtistDto.ScheduleEntry> schedule = staff.getSchedules().stream()
                .sorted((a, b) -> Integer.compare(a.getDayOfWeek().getOrder(), b.getDayOfWeek().getOrder()))
                .map(this::toScheduleEntry)
                .distinct()
                .toList();

        boolean isOpen = isCurrentlyOpen(staff.getSchedules());

        int experience = staff.getCreatedAt() != null
                ? (int) ChronoUnit.YEARS.between(
                        staff.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())
                : 0;

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
                staff.getInstagram(),
                isOpen,
                0,
                portfolioService.getShowcaseUrls(staff.getId()),
                schedule,
                Collections.emptyList(),
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

    private boolean isCurrentlyOpen(java.util.Collection<StaffSchedule> schedules) {
        java.time.DayOfWeek today = LocalDate.now().getDayOfWeek();
        LocalTime now = LocalTime.now();
        return schedules.stream()
                .filter(s -> s.getDayOfWeek().name().equals(today.name()))
                .anyMatch(s -> s.isWorkingAt(now));
    }
}
