package com.inkflow.crm.module.appointment.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.appointment.dto.*;
import com.inkflow.crm.module.appointment.service.AppointmentService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
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
            @RequestParam(required = false) List<UUID> artistIds,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        AppointmentFilterRequest filter = new AppointmentFilterRequest(locationId, artistIds, serviceId, status, from, to);
        PageResult<AppointmentDto> result = appointmentService.getAllAppointments(pageRequest, filter);

        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/client/{clientId}")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<List<AppointmentDto>>> getClientHistory(
            @PathVariable UUID clientId,
            @ModelAttribute PageRequest pageRequest) {
        return ResponseEntity.ok(ApiResponse.success(appointmentService.getClientHistory(clientId, pageRequest)));
    }

    @GetMapping("/{id}")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<AppointmentDetailDto>> getAppointment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(appointmentService.getAppointmentById(id)));
    }

    @GetMapping("/calendar")
    @RequirePermission({"calendar.view_all", "calendar.view_own"})
    public ResponseEntity<ApiResponse<List<AppointmentDto>>> getCalendar(@Valid @ModelAttribute CalendarQueryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(appointmentService.getCalendar(request)));
    }

    @PostMapping
    @RequirePermission("calendar.create")
    public ResponseEntity<ApiResponse<AppointmentDto>> createAppointment(@Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentDto appointment = appointmentService.createAppointment(request);
        log.info("Appointment created via API: appointmentId={}", appointment.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(appointment));
    }

    @PatchMapping("/{id}")
    @RequirePermission("calendar.edit")
    public ResponseEntity<ApiResponse<AppointmentDto>> updateAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppointmentRequest request) {
        AppointmentDto appointment = appointmentService.updateAppointment(id, request);
        log.info("Appointment updated via API: appointmentId={}", id);

        return ResponseEntity.ok(ApiResponse.success(appointment));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("calendar.cancel")
    public ResponseEntity<ApiResponse<Void>> deleteAppointment(@PathVariable UUID id) {
        appointmentService.deleteAppointment(id);
        log.info("Appointment deleted via API: appointmentId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/{id}/photos")
    @RequirePermission("calendar.edit")
    public ResponseEntity<ApiResponse<AppointmentDetailDto.PhotoDto>> addPhoto(
            @PathVariable UUID id,
            @Valid @RequestBody AddAppointmentPhotoRequest request) {
        AppointmentDetailDto.PhotoDto photo = appointmentService.addPhoto(id, request.getUrl(), request.getStage());
        log.info("Appointment photo added via API: appointmentId={} photoId={}", id, photo.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(photo));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @RequirePermission("calendar.edit")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId) {
        appointmentService.deletePhoto(id, photoId);
        log.info("Appointment photo deleted via API: appointmentId={} photoId={}", id, photoId);

        return ResponseEntity.ok(ApiResponse.empty());
    }
}
