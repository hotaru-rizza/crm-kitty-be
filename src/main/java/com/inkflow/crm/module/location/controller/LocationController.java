package com.inkflow.crm.module.location.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.location.dto.*;
import com.inkflow.crm.module.location.service.LocationService;
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
@RequestMapping("/locations")
@RequiredArgsConstructor
@Tag(name = "CRM · Locations")
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    @RequirePermission({Permission.LOCATIONS_VIEW, Permission.SETTINGS_ACCESS})
    public ResponseEntity<ApiResponse<List<LocationDto>>> getAllLocations(@ModelAttribute PageRequest pageRequest) {
        PageResult<LocationDto> result = locationService.getAllLocations(pageRequest);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    @RequirePermission({Permission.LOCATIONS_VIEW, Permission.SETTINGS_ACCESS})
    public ResponseEntity<ApiResponse<LocationDetailDto>> getLocation(@PathVariable UUID id) {
        LocationDetailDto location = locationService.getLocationById(id);
        return ResponseEntity.ok(ApiResponse.success(location));
    }

    @PostMapping
    @RequirePermission(Permission.LOCATIONS_EDIT)
    public ResponseEntity<ApiResponse<LocationDto>> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        LocationDto location = locationService.createLocation(request);
        log.info("Location created via API: locationId={}", location.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(location));
    }

    @PatchMapping("/{id}")
    @RequirePermission(Permission.LOCATIONS_EDIT)
    public ResponseEntity<ApiResponse<LocationDto>> updateLocation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocationRequest request) {
        LocationDto location = locationService.updateLocation(id, request);
        log.info("Location updated via API: locationId={}", id);

        return ResponseEntity.ok(ApiResponse.success(location));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(Permission.LOCATIONS_EDIT)
    public ResponseEntity<ApiResponse<Void>> deleteLocation(@PathVariable UUID id) {
        locationService.deleteLocation(id);
        log.info("Location deleted via API: locationId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/{id}/staff")
    @RequirePermission(Permission.LOCATIONS_EDIT)
    public ResponseEntity<ApiResponse<Void>> assignStaff(
            @PathVariable UUID id,
            @Valid @RequestBody AssignStaffRequest request) {
        locationService.assignStaff(id, request);
        log.info("Location staff assigned via API: locationId={} count={}", id, request.getStaffIds().size());

        return ResponseEntity.ok(ApiResponse.empty());
    }
}
