package com.inkflow.crm.module.audit.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.audit.dto.AuditLogDto;
import com.inkflow.crm.module.audit.service.AuditLogService;
import com.inkflow.crm.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/audit-log")
@RequiredArgsConstructor
@Tag(name = "CRM · Audit")
public class AuditLogController {

    private final AuditLogService service;

    @GetMapping
    @RequirePermission(Permission.CALENDAR_VIEW_ALL)
    public ResponseEntity<ApiResponse<List<AuditLogDto>>> getLog(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) List<UUID> actorIds,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) List<String> actions,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponses.page(
                service.getLog(
                        mergeActorIds(actorId, actorIds),
                        clientId,
                        actions,
                        entityType,
                        from,
                        to,
                        page,
                        Math.min(size, 100)
                )
        );
    }

    private List<UUID> mergeActorIds(UUID actorId, List<UUID> actorIds) {
        LinkedHashSet<UUID> merged = new LinkedHashSet<>();
        if (actorIds != null) {
            merged.addAll(actorIds);
        }
        if (actorId != null) {
            merged.add(actorId);
        }
        return merged.isEmpty() ? List.of() : new ArrayList<>(merged);
    }
}
