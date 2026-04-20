package com.inkflow.crm.module.location.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.location.dto.*;
import com.inkflow.crm.module.location.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LocationDto>>> getAllLocations(@ModelAttribute PageRequest pageRequest) {
        PageResult<LocationDto> result = locationService.getAllLocations(pageRequest);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LocationDetailDto>> getLocation(@PathVariable UUID id) {
        LocationDetailDto location = locationService.getLocationById(id);
        return ResponseEntity.ok(ApiResponse.success(location));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LocationDto>> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        LocationDto location = locationService.createLocation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(location));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<LocationDto>> updateLocation(@PathVariable UUID id, @Valid @RequestBody UpdateLocationRequest request) {
        LocationDto location = locationService.updateLocation(id, request);
        return ResponseEntity.ok(ApiResponse.success(location));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLocation(@PathVariable UUID id) {
        locationService.deleteLocation(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/{id}/staff")
    public ResponseEntity<ApiResponse<Void>> assignStaff(@PathVariable UUID id, @Valid @RequestBody AssignStaffRequest request) {
        locationService.assignStaff(id, request);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
