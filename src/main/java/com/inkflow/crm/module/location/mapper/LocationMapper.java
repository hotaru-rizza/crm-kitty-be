package com.inkflow.crm.module.location.mapper;

import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.module.location.dto.CreateLocationRequest;
import com.inkflow.crm.module.location.dto.LocationDetailDto;
import com.inkflow.crm.module.location.dto.LocationDto;
import com.inkflow.crm.module.location.dto.UpdateLocationRequest;
import com.inkflow.crm.module.staff.dto.StaffSummaryDto;
import org.mapstruct.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    DateTimeFormatter WORKING_HOURS_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    LocationDto toDto(Location location);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "staff", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "workingHoursStart", ignore = true)
    @Mapping(target = "workingHoursEnd", ignore = true)
    Location toEntity(CreateLocationRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "staff", ignore = true)
    @Mapping(target = "workingHoursStart", ignore = true)
    @Mapping(target = "workingHoursEnd", ignore = true)
    void updateEntity(UpdateLocationRequest request, @MappingTarget Location location);

    @AfterMapping
    default void applyCreateWorkingHours(CreateLocationRequest request, @MappingTarget Location location) {
        location.setWorkingHoursStart(resolveWorkingHoursStart(request.getWorkingHoursStart()));
        location.setWorkingHoursEnd(resolveWorkingHoursEnd(request.getWorkingHoursEnd()));
    }

    @AfterMapping
    default void applyUpdateWorkingHours(UpdateLocationRequest request, @MappingTarget Location location) {
        if (request.getWorkingHoursStart() != null) {
            location.setWorkingHoursStart(parseWorkingHours(request.getWorkingHoursStart()));
        }
        if (request.getWorkingHoursEnd() != null) {
            location.setWorkingHoursEnd(parseWorkingHours(request.getWorkingHoursEnd()));
        }
    }

    default LocalTime resolveWorkingHoursStart(String value) {
        return value != null && !value.isBlank()
                ? parseWorkingHours(value)
                : Location.DEFAULT_WORKING_HOURS_START;
    }

    default LocalTime resolveWorkingHoursEnd(String value) {
        return value != null && !value.isBlank()
                ? parseWorkingHours(value)
                : Location.DEFAULT_WORKING_HOURS_END;
    }

    default LocalTime parseWorkingHours(String value) {
        return value != null && !value.isBlank() ? LocalTime.parse(value) : null;
    }

    default String formatWorkingHours(LocalTime value) {
        return value != null ? value.format(WORKING_HOURS_FORMAT) : null;
    }

    default LocationDetailDto toDetailDto(Location location, LocationDetailDto.LocationStatsDto stats) {
        List<StaffSummaryDto> staffList = location.getStaff().stream()
                .filter(s -> s.getDeletedAt() == null)
                .map(s -> StaffSummaryDto.builder()
                        .id(s.getId())
                        .firstName(s.getFirstName())
                        .lastName(s.getLastName())
                        .avatar(s.getAvatar())
                        .calendarColor(s.getCalendarColor())
                        .role(s.getRole().getValue())
                        .build())
                .collect(Collectors.toList());

        return LocationDetailDto.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .city(location.getCity())
                .phone(location.getPhone())
                .googleMapsLink(location.getGoogleMapsLink())
                .color(location.getColor())
                .isActive(location.getIsActive())
                .photoUrl(location.getPhotoUrl())
                .navigationInstructions(location.getNavigationInstructions())
                .telegramContact(location.getTelegramContact())
                .instagram(location.getInstagram())
                .staff(staffList)
                .stats(stats)
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }

    default LocationDto toDtoWithStaffCount(Location location) {
        return LocationDto.builder()
                .id(location.getId())
                .name(location.getName())
                .address(location.getAddress())
                .phone(location.getPhone())
                .googleMapsLink(location.getGoogleMapsLink())
                .color(location.getColor())
                .isActive(location.getIsActive())
                .photoUrl(location.getPhotoUrl())
                .navigationInstructions(location.getNavigationInstructions())
                .telegramContact(location.getTelegramContact())
                .instagram(location.getInstagram())
                .city(location.getCity())
                .workingHoursStart(formatWorkingHours(location.getWorkingHoursStart()))
                .workingHoursEnd(formatWorkingHours(location.getWorkingHoursEnd()))
                .staffCount((int) location.getStaff().stream().filter(s -> s.getDeletedAt() == null).count())
                .createdAt(location.getCreatedAt())
                .build();
    }
}
