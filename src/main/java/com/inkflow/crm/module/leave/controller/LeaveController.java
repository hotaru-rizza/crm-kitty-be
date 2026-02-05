package com.inkflow.crm.module.leave.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.leave.dto.CreateLeaveRequest;
import com.inkflow.crm.module.leave.dto.LeaveRequestDto;
import com.inkflow.crm.module.leave.dto.UpdateLeaveStatusRequest;
import com.inkflow.crm.module.leave.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping("/staff/{staffId}")
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
    public ResponseEntity<ApiResponse<LeaveRequestDto>> getLeaveById(@PathVariable UUID id) {
        LeaveRequestDto leave = leaveService.getLeaveById(id);
        return ResponseEntity.ok(ApiResponse.success(leave));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LeaveRequestDto>> createLeave(
            @Valid @RequestBody CreateLeaveRequest request) {
        LeaveRequestDto leave = leaveService.createLeave(request);
        return ResponseEntity.ok(ApiResponse.success(leave));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<LeaveRequestDto>> updateLeaveStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLeaveStatusRequest request) {
        LeaveRequestDto leave = leaveService.updateLeaveStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(leave));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLeave(@PathVariable UUID id) {
        leaveService.deleteLeave(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/staff/{staffId}/check")
    public ResponseEntity<ApiResponse<Boolean>> checkStaffOnLeave(
            @PathVariable UUID staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        boolean onLeave = leaveService.isStaffOnLeave(staffId, date);
        return ResponseEntity.ok(ApiResponse.success(onLeave));
    }
}
