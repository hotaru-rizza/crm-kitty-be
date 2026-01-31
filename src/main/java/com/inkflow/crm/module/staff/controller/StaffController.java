package com.inkflow.crm.module.staff.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.staff.dto.*;
import com.inkflow.crm.module.staff.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffDto>>> getAllStaff(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) String role) {
        List<StaffDto> staff = staffService.getAllStaff(pageRequest, role);
        PaginationDto pagination = staffService.getPagination(pageRequest);
        return ResponseEntity.ok(ApiResponse.success(staff, pagination));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffDetailDto>> getStaff(@PathVariable UUID id) {
        StaffDetailDto staff = staffService.getStaffById(id);
        return ResponseEntity.ok(ApiResponse.success(staff));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StaffDto>> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        StaffDto staff = staffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(staff));
    }

    @PatchMapping("/{id}")
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
    public ResponseEntity<ApiResponse<Void>> updateSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduleRequest request) {
        staffService.updateSchedule(id, request);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<Map<String, String>>> inviteStaff(@Valid @RequestBody InviteStaffRequest request) {
        String token = staffService.inviteStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("token", token)));
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<ApiResponse<StaffDto>> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        StaffDto staff = staffService.acceptInvite(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(staff));
    }
}
