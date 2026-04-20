package com.inkflow.crm.module.staff.mapper;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.staff.dto.*;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface StaffMapper {

    @Mapping(target = "role", expression = "java(staff.getRole().getValue())")
    @Mapping(target = "status", expression = "java(staff.getStatus().getValue())")
    @Mapping(target = "locationIds", expression = "java(staff.getLocations().stream().map(l -> l.getId()).collect(java.util.stream.Collectors.toList()))")
    @Mapping(target = "specialization", expression = "java(new java.util.ArrayList<>(staff.getSpecialization()))")
    StaffDto toDto(Staff staff);

    List<StaffDto> toDtoList(List<Staff> staffList);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "locations", ignore = true)
    @Mapping(target = "schedules", ignore = true)
    @Mapping(target = "servicePricings", ignore = true)
    @Mapping(target = "authUserId", ignore = true)
    @Mapping(target = "status", expression = "java(com.inkflow.crm.domain.enums.StaffStatus.WORKING)")
    @Mapping(target = "role", expression = "java(mapRole(request.getRole()))")
    Staff toEntity(CreateStaffRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "locations", ignore = true)
    @Mapping(target = "schedules", ignore = true)
    @Mapping(target = "servicePricings", ignore = true)
    @Mapping(target = "authUserId", ignore = true)
    @Mapping(target = "status", expression = "java(request.getStatus() != null ? mapStatus(request.getStatus()) : staff.getStatus())")
    void updateEntity(UpdateStaffRequest request, @MappingTarget Staff staff);

    default StaffSummaryDto toSummaryDto(Staff staff) {
        return StaffSummaryDto.builder()
                .id(staff.getId())
                .firstName(staff.getFirstName())
                .lastName(staff.getLastName())
                .avatar(staff.getAvatar())
                .calendarColor(staff.getCalendarColor())
                .role(staff.getRole().getValue())
                .build();
    }

    default UserRole mapRole(String role) {
        return UserRole.fromValue(role);
    }

    default StaffStatus mapStatus(String status) {
        return StaffStatus.fromValue(status);
    }
}
