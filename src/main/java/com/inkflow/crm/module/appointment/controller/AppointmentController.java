package com.inkflow.crm.module.appointment.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.appointment.dto.*;
import com.inkflow.crm.module.appointment.service.AppointmentService;
import jakarta.validation.Valid;
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
    public ResponseEntity<ApiResponse<List<AppointmentDto>>> getAllAppointments(@ModelAttribute PageRequest pageRequest) {
        List<AppointmentDto> appointments = appointmentService.getAllAppointments(pageRequest);
        PaginationDto pagination = appointmentService.getPagination(pageRequest);
        return ResponseEntity.ok(ApiResponse.success(appointments, pagination));
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
}
