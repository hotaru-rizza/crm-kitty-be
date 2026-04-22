package com.inkflow.crm.module.staff.mapper;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.SalaryType;
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
    @Mapping(target = "salaryType", expression = "java(staff.getSalaryType() != null ? staff.getSalaryType().getValue() : \"none\")")
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
    @Mapping(target = "salaryType", expression = "java(com.inkflow.crm.domain.enums.SalaryType.NONE)")
    @Mapping(target = "salaryRate", ignore = true)
    @Mapping(target = "bankDetails", ignore = true)
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
    @Mapping(target = "salaryType", expression = "java(request.getSalaryType() != null ? mapSalaryType(request.getSalaryType()) : staff.getSalaryType())")
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

    default SalaryType mapSalaryType(String type) {
        return SalaryType.fromValue(type);
    }
}
