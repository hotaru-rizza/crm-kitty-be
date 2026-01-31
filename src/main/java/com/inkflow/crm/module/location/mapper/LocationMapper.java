package com.inkflow.crm.module.location.mapper;

import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.module.location.dto.CreateLocationRequest;
import com.inkflow.crm.module.location.dto.LocationDetailDto;
import com.inkflow.crm.module.location.dto.LocationDto;
import com.inkflow.crm.module.staff.dto.StaffSummaryDto;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    LocationDto toDto(Location location);

    List<LocationDto> toDtoList(List<Location> locations);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "staff", ignore = true)
    @Mapping(target = "isActive", constant = "true")
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
    void updateEntity(com.inkflow.crm.module.location.dto.UpdateLocationRequest request, @MappingTarget Location location);

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
                .phone(location.getPhone())
                .googleMapsLink(location.getGoogleMapsLink())
                .color(location.getColor())
                .isActive(location.getIsActive())
                .staff(staffList)
                .stats(stats)
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}
