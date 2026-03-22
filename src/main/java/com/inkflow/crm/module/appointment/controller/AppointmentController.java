package com.inkflow.crm.module.appointment.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.appointment.dto.*;
import com.inkflow.crm.module.appointment.service.AppointmentService;
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
    public ResponseEntity<ApiResponse<List<AppointmentDto>>> getAllAppointments(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID artistId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        AppointmentFilterRequest filter = new AppointmentFilterRequest(locationId, artistId, serviceId, status, from, to);
        List<AppointmentDto> appointments = appointmentService.getAllAppointments(pageRequest, filter);
        PaginationDto pagination = appointmentService.getPagination(pageRequest, filter);
        return ResponseEntity.ok(ApiResponse.success(appointments, pagination));
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
    public ResponseEntity<ApiResponse<List<AppointmentDto>>> getClientHistory(
            @PathVariable UUID clientId,
            @ModelAttribute PageRequest pageRequest) {
        List<AppointmentDto> appointments = appointmentService.getClientHistory(clientId, pageRequest);
        return ResponseEntity.ok(ApiResponse.success(appointments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentDetailDto>> getAppointment(@PathVariable UUID id) {
        AppointmentDetailDto appointment = appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(ApiResponse.success(appointment));
    }

    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<AppointmentDto>>> getCalendar(@Valid @ModelAttribute CalendarQueryRequest request) {
        List<AppointmentDto> appointments = appointmentService.getCalendar(request);
        return ResponseEntity.ok(ApiResponse.success(appointments));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentDto>> createAppointment(@Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentDto appointment = appointmentService.createAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(appointment));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentDto>> updateAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppointmentRequest request) {
        AppointmentDto appointment = appointmentService.updateAppointment(id, request);
        return ResponseEntity.ok(ApiResponse.success(appointment));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAppointment(@PathVariable UUID id) {
        appointmentService.deleteAppointment(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/{id}/photos")
    public ResponseEntity<ApiResponse<AppointmentDetailDto.PhotoDto>> addPhoto(
            @PathVariable UUID id,
            @Valid @RequestBody AddPhotoRequest request) {
        AppointmentDetailDto.PhotoDto photo = appointmentService.addPhoto(id, request.getUrl(), request.getStage());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(photo));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
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
