package com.inkflow.crm.module.appointment.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.appointment.dto.*;
import com.inkflow.crm.module.appointment.service.AppointmentService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<List<AppointmentDto>>> getAllAppointments(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID artistId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        AppointmentFilterRequest filter = new AppointmentFilterRequest(locationId, artistId, serviceId, status, from, to);
        PageResult<AppointmentDto> result = appointmentService.getAllAppointments(pageRequest, filter);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class AppointmentFilterRequest {
        private UUID locationId;
        private UUID artistId;
        private UUID serviceId;
        private String status;
        private String from;
        private String to;
    }

    @GetMapping("/client/{clientId}")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<List<AppointmentDto>>> getClientHistory(
            @PathVariable UUID clientId,
            @ModelAttribute PageRequest pageRequest) {
        List<AppointmentDto> appointments = appointmentService.getClientHistory(clientId, pageRequest);
        return ResponseEntity.ok(ApiResponse.success(appointments));
    }

    @GetMapping("/{id}")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<AppointmentDetailDto>> getAppointment(@PathVariable UUID id) {
        AppointmentDetailDto appointment = appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(ApiResponse.success(appointment));
    }

    @GetMapping("/calendar")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<List<AppointmentDto>>> getCalendar(@Valid @ModelAttribute CalendarQueryRequest request) {
        List<AppointmentDto> appointments = appointmentService.getCalendar(request);
        return ResponseEntity.ok(ApiResponse.success(appointments));
    }

    @PostMapping
    @RequirePermission("calendar.create")
    public ResponseEntity<ApiResponse<AppointmentDto>> createAppointment(@Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentDto appointment = appointmentService.createAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(appointment));
    }

    @PatchMapping("/{id}")
    @RequirePermission("calendar.edit")
    public ResponseEntity<ApiResponse<AppointmentDto>> updateAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppointmentRequest request) {
        AppointmentDto appointment = appointmentService.updateAppointment(id, request);
        return ResponseEntity.ok(ApiResponse.success(appointment));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("calendar.cancel")
    public ResponseEntity<ApiResponse<Void>> deleteAppointment(@PathVariable UUID id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/{id}/photos")
    @RequirePermission("calendar.edit")
    public ResponseEntity<ApiResponse<AppointmentDetailDto.PhotoDto>> addPhoto(
            @PathVariable UUID id,
            @Valid @RequestBody AddPhotoRequest request) {
        AppointmentDetailDto.PhotoDto photo = appointmentService.addPhoto(id, request.getUrl(), request.getStage());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(photo));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @RequirePermission("calendar.edit")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId) {
        appointmentService.deletePhoto(id, photoId);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @Data
    public static class AddPhotoRequest {
        @NotBlank
        private String url;
        @NotBlank
        private String stage;
    }
}
