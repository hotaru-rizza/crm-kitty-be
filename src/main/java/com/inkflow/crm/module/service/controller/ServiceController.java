package com.inkflow.crm.module.service.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.service.dto.*;
import com.inkflow.crm.module.service.service.ServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceService serviceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServiceDto>>> getAllServices(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) Boolean active) {
        PageResult<ServiceDto> result = serviceService.getAllServices(pageRequest, active);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceDetailDto>> getService(@PathVariable UUID id) {
        ServiceDetailDto service = serviceService.getServiceById(id);
        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @GetMapping("/{id}/price")
    public ResponseEntity<ApiResponse<ServicePriceDto>> getServicePrice(
            @PathVariable UUID id,
            @RequestParam UUID artistId) {
        ServicePriceDto price = serviceService.getServicePrice(id, artistId);
        return ResponseEntity.ok(ApiResponse.success(price));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ServiceDto>> createService(@Valid @RequestBody CreateServiceRequest request) {
        ServiceDto service = serviceService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceDto>> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateServiceRequest request) {
        ServiceDto service = serviceService.updateService(id, request);
        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteService(@PathVariable UUID id) {
        serviceService.deleteService(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
