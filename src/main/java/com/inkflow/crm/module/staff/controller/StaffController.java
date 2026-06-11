package com.inkflow.crm.module.staff.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.staff.dto.*;
import com.inkflow.crm.module.staff.service.*;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;
    private final StaffDetailService staffDetailService;
    private final StaffInviteService staffInviteService;
    private final StaffScheduleService staffScheduleService;
    private final StaffPricingService staffPricingService;
    private final StaffLifecycleService staffLifecycleService;
    private final StaffFaqService staffFaqService;

    @GetMapping
    @RequirePermission(Permission.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<StaffDto>>> getAllStaff(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String accountStatus) {
        PageResult<StaffDto> result = staffService.getAllStaff(pageRequest, search, role, locationId, accountStatus);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    @RequirePermission(Permission.STAFF_VIEW)
    public ResponseEntity<ApiResponse<StaffDetailDto>> getStaff(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(staffDetailService.getDetail(id)));
    }

    @PostMapping
    @RequirePermission(Permission.STAFF_INVITE)
    public ResponseEntity<ApiResponse<StaffDto>> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        StaffDto staff = staffService.createStaff(request);
        log.info("Staff created via API: staffId={}", staff.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(staff));
    }

    @PatchMapping("/{id}")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<StaffDto>> updateStaff(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRequest request) {
        StaffDto staff = staffService.updateStaff(id, request);
        log.info("Staff updated via API: staffId={}", id);

        return ResponseEntity.ok(ApiResponse.success(staff));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable UUID id) {
        staffService.deleteStaff(id);
        log.info("Staff deleted via API: staffId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PutMapping("/{id}/schedule")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<Void>> updateSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduleRequest request) {
        staffScheduleService.updateSchedule(id, request);
        log.info("Staff schedule updated via API: staffId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/invite/info/{token}")
    public ResponseEntity<ApiResponse<InviteInfoDto>> getInviteInfo(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(staffInviteService.getInviteInfo(token)));
    }

    @PostMapping("/invite")
    @RequirePermission(Permission.STAFF_INVITE)
    public ResponseEntity<ApiResponse<Map<String, String>>> inviteStaff(@Valid @RequestBody InviteStaffRequest request) {
        String token = staffInviteService.inviteStaff(request);
        log.info("Staff invite sent via API: email={}", request.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("token", token)));
    }

    @PostMapping("/accept-invite")
    public ResponseEntity<ApiResponse<StaffDto>> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        StaffDto staff = staffInviteService.acceptInvite(request);
        log.info("Staff invite accepted via API: staffId={}", staff.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(staff));
    }

    @GetMapping("/{id}/services")
    @RequirePermission(Permission.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<StaffServiceDto>>> getStaffServices(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(staffPricingService.getStaffServices(id)));
    }

    @PutMapping("/{id}/services")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<List<StaffServiceDto>>> updateStaffServices(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffServicesRequest request) {
        List<StaffServiceDto> services = staffPricingService.updateStaffServices(id, request);
        log.info("Staff services bulk-updated via API: staffId={} count={}", id, services.size());

        return ResponseEntity.ok(ApiResponse.success(services));
    }

    @PostMapping("/{id}/services/{serviceId}")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<StaffServiceDto>> addServiceToStaff(
            @PathVariable UUID id,
            @PathVariable UUID serviceId,
            @RequestBody(required = false) AddStaffServiceRequest request) {
        BigDecimal customPrice = request != null ? request.getCustomPrice() : null;
        Integer customDuration = request != null ? request.getCustomDuration() : null;
        StaffServiceDto service = staffPricingService.addServiceToStaff(id, serviceId, customPrice, customDuration);
        log.info("Staff service added via API: staffId={} serviceId={}", id, serviceId);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service));
    }

    @PutMapping("/{id}/services/{serviceId}")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<StaffServiceDto>> updateStaffServicePricing(
            @PathVariable UUID id,
            @PathVariable UUID serviceId,
            @Valid @RequestBody AddStaffServiceRequest request) {
        BigDecimal customPrice = request != null ? request.getCustomPrice() : null;
        Integer customDuration = request != null ? request.getCustomDuration() : null;
        StaffServiceDto service = staffPricingService.updateStaffServicePricing(id, serviceId, customPrice, customDuration);
        log.info("Staff service pricing updated via API: staffId={} serviceId={}", id, serviceId);

        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @DeleteMapping("/{id}/services/{serviceId}")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<Void>> removeServiceFromStaff(
            @PathVariable UUID id,
            @PathVariable UUID serviceId) {
        staffPricingService.removeServiceFromStaff(id, serviceId);
        log.info("Staff service removed via API: staffId={} serviceId={}", id, serviceId);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/{id}/future-appointments-count")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getFutureAppointmentsCount(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", staffLifecycleService.getFutureAppointmentsCount(id))));
    }

    @PostMapping("/{id}/reactivate")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<Void>> reactivateStaff(@PathVariable UUID id) {
        staffLifecycleService.reactivateStaff(id);
        log.info("Staff reactivated via API: staffId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/{id}/deactivate")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<Void>> deactivateStaff(
            @PathVariable UUID id,
            @RequestBody(required = false) DeactivateStaffRequest request) {
        staffLifecycleService.deactivateStaff(id, request != null ? request : new DeactivateStaffRequest(false));
        log.info("Staff deactivated via API: staffId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/{id}/faq")
    @RequirePermission(Permission.STAFF_VIEW)
    public ResponseEntity<ApiResponse<List<StaffFaqDto>>> getFaq(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(staffFaqService.getFaq(id)));
    }

    @PutMapping("/{id}/faq")
    @RequirePermission(Permission.STAFF_EDIT)
    public ResponseEntity<ApiResponse<List<StaffFaqDto>>> upsertFaq(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertFaqRequest request) {
        List<StaffFaqDto> faq = staffFaqService.upsertFaq(id, request);
        log.info("Staff FAQ upserted via API: staffId={} count={}", id, faq.size());

        return ResponseEntity.ok(ApiResponse.success(faq));
    }
}
