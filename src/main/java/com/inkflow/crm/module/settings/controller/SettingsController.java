package com.inkflow.crm.module.settings.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.settings.dto.CompanySettingsDto;
import com.inkflow.crm.module.settings.dto.UpdateCompanySettingsRequest;
import com.inkflow.crm.module.settings.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping({"", "/company"})
    public ResponseEntity<ApiResponse<CompanySettingsDto>> getCompanySettings() {
        CompanySettingsDto settings = settingsService.getCompanySettings();
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @PatchMapping({"", "/company"})
    public ResponseEntity<ApiResponse<CompanySettingsDto>> updateCompanySettings(
            @Valid @RequestBody UpdateCompanySettingsRequest request) {
        CompanySettingsDto settings = settingsService.updateCompanySettings(request);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }
}
