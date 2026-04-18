package com.inkflow.crm.module.staff.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.staff.dto.*;
import com.inkflow.crm.security.RequirePermission;
import com.inkflow.crm.module.staff.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    @RequirePermission("staff.view")
    public ResponseEntity<ApiResponse<List<StaffDto>>> getAllStaff(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UUID locationId) {
        List<StaffDto> staff = staffService.getAllStaff(pageRequest, search, role, locationId);
        PaginationDto pagination = staffService.getPagination(pageRequest, search, role, locationId);
        return ResponseEntity.ok(ApiResponse.success(staff, pagination));
    }

    @GetMapping("/{id}")
    @RequirePermission("staff.view")
    public ResponseEntity<ApiResponse<StaffDetailDto>> getStaff(@PathVariable UUID id) {
        StaffDetailDto staff = staffService.getStaffById(id);
        return ResponseEntity.ok(ApiResponse.success(staff));
    }

    @PostMapping
    @RequirePermission("staff.invite")
    public ResponseEntity<ApiResponse<StaffDto>> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        StaffDto staff = staffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(staff));
    }

    @PatchMapping("/{id}")
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<StaffDto>> updateStaff(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRequest request) {
        StaffDto staff = staffService.updateStaff(id, request);
        return ResponseEntity.ok(ApiResponse.success(staff));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable UUID id) {
        staffService.deleteStaff(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PutMapping("/{id}/schedule")
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<Void>> updateSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduleRequest request) {
        staffService.updateSchedule(id, request);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/invite/info/{token}")
    public ResponseEntity<ApiResponse<com.inkflow.crm.module.staff.dto.InviteInfoDto>> getInviteInfo(
            @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(staffService.getInviteInfo(token)));
    }

    @PostMapping("/invite")
    @RequirePermission("staff.invite")
    public ResponseEntity<ApiResponse<Map<String, String>>> inviteStaff(@Valid @RequestBody InviteStaffRequest request) {
        String token = staffService.inviteStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("token", token)));
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<ApiResponse<StaffDto>> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        StaffDto staff = staffService.acceptInvite(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(staff));
    }

    // ======== Staff Services Management ========

    @GetMapping("/{id}/services")
    @RequirePermission("staff.view")
    public ResponseEntity<ApiResponse<List<StaffServiceDto>>> getStaffServices(@PathVariable UUID id) {
        List<StaffServiceDto> services = staffService.getStaffServices(id);
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    @PutMapping("/{id}/services")
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<List<StaffServiceDto>>> updateStaffServices(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffServicesRequest request) {
        List<StaffServiceDto> services = staffService.updateStaffServices(id, request);
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    @PostMapping("/{id}/services/{serviceId}")
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<StaffServiceDto>> addServiceToStaff(
            @PathVariable UUID id,
            @PathVariable UUID serviceId,
            @RequestBody(required = false) AddStaffServiceRequest request) {
        BigDecimal customPrice = request != null ? request.getCustomPrice() : null;
        Integer customDuration = request != null ? request.getCustomDuration() : null;
        StaffServiceDto service = staffService.addServiceToStaff(id, serviceId, customPrice, customDuration);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service));
    }

    @PutMapping("/{id}/services/{serviceId}")
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<StaffServiceDto>> updateStaffServicePricing(
            @PathVariable UUID id,
            @PathVariable UUID serviceId,
            @Valid @RequestBody AddStaffServiceRequest request) {
        BigDecimal customPrice = request != null ? request.getCustomPrice() : null;
        Integer customDuration = request != null ? request.getCustomDuration() : null;
        StaffServiceDto service = staffService.updateStaffServicePricing(id, serviceId, customPrice, customDuration);
        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @DeleteMapping("/{id}/services/{serviceId}")
    @RequirePermission("staff.edit")
    public ResponseEntity<ApiResponse<Void>> removeServiceFromStaff(
            @PathVariable UUID id,
            @PathVariable UUID serviceId) {
        staffService.removeServiceFromStaff(id, serviceId);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
