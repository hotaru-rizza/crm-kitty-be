package com.inkflow.crm.module.leave.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.leave.dto.CreateLeaveRequest;
import com.inkflow.crm.module.leave.dto.LeaveRequestDto;
import com.inkflow.crm.module.leave.dto.UpdateLeaveStatusRequest;
import com.inkflow.crm.module.leave.service.LeaveService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
@Tag(name = "CRM · Leave")
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping
    @RequirePermission({Permission.LEAVES_VIEW, Permission.LEAVES_MANAGE, Permission.CALENDAR_VIEW_ALL})
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> getAllLeaves(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String leaveType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) List<UUID> staffIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        Page<LeaveRequestDto> result = leaveService.getAllLeaves(status, leaveType, fromDate, toDate, locationId, staffIds, page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), PaginationDto.from(result)));
    }

    @GetMapping("/pending-count")
    @RequirePermission({Permission.LEAVES_MANAGE, Permission.CALENDAR_VIEW_ALL})
    public ResponseEntity<ApiResponse<Long>> getPendingCount(
            @RequestParam(required = false) UUID locationId) {
        long count = leaveService.getPendingCount(locationId);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @GetMapping("/staff/{staffId}")
    @RequirePermission({Permission.LEAVES_VIEW, Permission.LEAVES_MANAGE, Permission.CALENDAR_VIEW_ALL, Permission.CALENDAR_VIEW_OWN})
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> getLeavesByStaffId(
            @PathVariable UUID staffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<LeaveRequestDto> leaves;
        if (startDate != null && endDate != null) {
            leaves = leaveService.getLeavesByStaffIdAndDateRange(staffId, startDate, endDate);
        } else {
            leaves = leaveService.getLeavesByStaffId(staffId);
        }
        return ResponseEntity.ok(ApiResponse.success(leaves));
    }

    @GetMapping("/{id}")
    @RequirePermission({Permission.LEAVES_VIEW, Permission.LEAVES_MANAGE, Permission.CALENDAR_VIEW_ALL, Permission.CALENDAR_VIEW_OWN})
    public ResponseEntity<ApiResponse<LeaveRequestDto>> getLeaveById(@PathVariable UUID id) {
        LeaveRequestDto leave = leaveService.getLeaveById(id);
        return ResponseEntity.ok(ApiResponse.success(leave));
    }

    @PostMapping
    @RequirePermission({Permission.LEAVES_CREATE, Permission.LEAVES_MANAGE})
    public ResponseEntity<ApiResponse<LeaveRequestDto>> createLeave(
            @Valid @RequestBody CreateLeaveRequest request) {
        LeaveRequestDto leave = leaveService.createLeave(request);
        log.info("Leave created via API: leaveId={}", leave.getId());

        return ResponseEntity.ok(ApiResponse.success(leave));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission(Permission.LEAVES_MANAGE)
    public ResponseEntity<ApiResponse<LeaveRequestDto>> updateLeaveStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLeaveStatusRequest request) {
        LeaveRequestDto leave = leaveService.updateLeaveStatus(id, request);
        log.info("Leave status updated via API: leaveId={} status={}", id, request.getStatus());

        return ResponseEntity.ok(ApiResponse.success(leave));
    }

    @PatchMapping("/{id}/cancel")
    @RequirePermission({Permission.LEAVES_CREATE, Permission.LEAVES_MANAGE})
    public ResponseEntity<ApiResponse<LeaveRequestDto>> cancelLeave(@PathVariable UUID id) {
        LeaveRequestDto leave = leaveService.cancelLeave(id);
        log.info("Leave cancelled via API: leaveId={}", id);

        return ResponseEntity.ok(ApiResponse.success(leave));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(Permission.LEAVES_MANAGE)
    public ResponseEntity<ApiResponse<Void>> deleteLeave(@PathVariable UUID id) {
        leaveService.deleteLeave(id);
        log.info("Leave deleted via API: leaveId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/staff/{staffId}/check")
    @RequirePermission({Permission.LEAVES_VIEW, Permission.CALENDAR_VIEW_ALL, Permission.CALENDAR_VIEW_OWN})
    public ResponseEntity<ApiResponse<Boolean>> checkStaffOnLeave(
            @PathVariable UUID staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        boolean onLeave = leaveService.isStaffOnLeave(staffId, date);
        return ResponseEntity.ok(ApiResponse.success(onLeave));
    }

    @GetMapping("/calendar")
    @RequirePermission({Permission.LEAVES_VIEW, Permission.CALENDAR_VIEW_ALL, Permission.CALENDAR_VIEW_OWN})
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> getLeavesForCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<LeaveRequestDto> leaves = leaveService.getApprovedLeavesForDateRange(from, to);
        return ResponseEntity.ok(ApiResponse.success(leaves));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate();
        }
    }
}
