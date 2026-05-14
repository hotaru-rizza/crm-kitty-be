package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.catalog.dto.PublicArtistDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicArtistService {

    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public List<PublicArtistDto> findAll() {
        return staffRepository.findAllPublicArtists().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PublicArtistDto> findById(UUID id) {
        return staffRepository.findPublicArtistById(id)
                .map(this::toDto);
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
                .toList();

        boolean isOpen = isCurrentlyOpen(staff.getSchedules());

        return new PublicArtistDto(
                staff.getId(),
                staff.getFullName(),
                staff.getAvatar(),
                staff.getBio(),
                staff.getHourlyRate(),
                studioName,
                studioAddress,
                studioPhoto,
                lat,
                lng,
                staff.getSpecialization(),
                staff.getDontDoList(),
                staff.getInstagram(),
                isOpen,
                staff.getPortfolioImages(),
                schedule
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

    private boolean isCurrentlyOpen(List<StaffSchedule> schedules) {
        java.time.DayOfWeek today = java.time.LocalDate.now().getDayOfWeek();
        LocalTime now = LocalTime.now();
        return schedules.stream()
                .filter(s -> s.getDayOfWeek().name().equals(today.name()))
                .anyMatch(s -> s.isWorkingAt(now));
    }
}
