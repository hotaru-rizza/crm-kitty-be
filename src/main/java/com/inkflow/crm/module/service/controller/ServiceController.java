package com.inkflow.crm.module.service.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.service.dto.*;
import com.inkflow.crm.module.service.service.ServiceService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
@Tag(name = "CRM · Services")
public class ServiceController {

    private final ServiceService serviceService;

    @GetMapping
    @RequirePermission({Permission.SERVICES_VIEW, Permission.SETTINGS_ACCESS})
    public ResponseEntity<ApiResponse<List<ServiceDto>>> getAllServices(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) Boolean active) {
        PageResult<ServiceDto> result = serviceService.getAllServices(pageRequest, active);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    @RequirePermission({Permission.SERVICES_VIEW, Permission.SETTINGS_ACCESS})
    public ResponseEntity<ApiResponse<ServiceDetailDto>> getService(@PathVariable UUID id) {
        ServiceDetailDto service = serviceService.getServiceById(id);
        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @GetMapping("/{id}/price")
    @RequirePermission({Permission.SERVICES_VIEW, Permission.CALENDAR_VIEW_ALL, Permission.CALENDAR_VIEW_OWN})
    public ResponseEntity<ApiResponse<ServicePriceDto>> getServicePrice(
            @PathVariable UUID id,
            @RequestParam UUID artistId) {
        ServicePriceDto price = serviceService.getServicePrice(id, artistId);
        return ResponseEntity.ok(ApiResponse.success(price));
    }

    @PostMapping
    @RequirePermission(Permission.SERVICES_EDIT)
    public ResponseEntity<ApiResponse<ServiceDto>> createService(@Valid @RequestBody CreateServiceRequest request) {
        ServiceDto service = serviceService.createService(request);
        log.info("Service created via API: serviceId={}", service.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service));
    }

    @PatchMapping("/{id}")
    @RequirePermission(Permission.SERVICES_EDIT)
    public ResponseEntity<ApiResponse<ServiceDto>> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceRequest request) {
        ServiceDto service = serviceService.updateService(id, request);
        log.info("Service updated via API: serviceId={}", id);

        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteService(@PathVariable UUID id) {
        serviceService.deleteService(id);
        log.info("Service deleted via API: serviceId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }
}
