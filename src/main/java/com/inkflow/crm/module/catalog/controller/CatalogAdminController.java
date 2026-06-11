package com.inkflow.crm.module.catalog.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.catalog.dto.CatalogRetagResultDto;
import com.inkflow.crm.module.catalog.dto.CatalogSeedResultDto;
import com.inkflow.crm.module.catalog.service.TattooCatalogService;
import com.inkflow.crm.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/catalog/admin/tattoos")
@RequiredArgsConstructor
public class CatalogAdminController {

    private final TattooCatalogService tattooCatalogService;

    @PostMapping("/seed")
    @RequirePermission(Permission.SETTINGS_ACCESS)
    public ResponseEntity<ApiResponse<CatalogSeedResultDto>> seed() {
        CatalogSeedResultDto result = tattooCatalogService.seed();
        log.info("Tattoo catalog seeded via API: saved={} total={}", result.saved(), result.total());

        return ApiResponses.ok(result);
    }

    @PostMapping("/retag")
    @RequirePermission(Permission.SETTINGS_ACCESS)
    public ResponseEntity<ApiResponse<CatalogRetagResultDto>> retag() {
        CatalogRetagResultDto result = tattooCatalogService.retag();
        log.info("Tattoo catalog retagged via API: retagged={}", result.retagged());

        return ApiResponses.ok(result);
    }
}
