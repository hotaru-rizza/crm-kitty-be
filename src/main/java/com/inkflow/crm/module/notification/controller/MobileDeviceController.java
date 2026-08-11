package com.inkflow.crm.module.notification.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.notification.dto.DeviceTokenDto;
import com.inkflow.crm.module.notification.dto.RegisterDeviceRequest;
import com.inkflow.crm.module.notification.dto.UnregisterDeviceRequest;
import com.inkflow.crm.module.notification.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/mobile/devices")
@RequiredArgsConstructor
@Tag(name = "CRM · Mobile Devices")
public class MobileDeviceController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceTokenDto>> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request) {
        DeviceTokenDto device = deviceTokenService.register(request);

        log.info("Device registered via API: deviceId={} platform={}", device.id(), device.platform());

        return ResponseEntity.ok(ApiResponse.success(device));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unregisterDevice(
            @Valid @RequestBody UnregisterDeviceRequest request) {
        deviceTokenService.unregister(request);

        log.info("Device unregistered via API");

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
