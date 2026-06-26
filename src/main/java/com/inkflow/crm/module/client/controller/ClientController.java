package com.inkflow.crm.module.client.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.client.dto.*;
import com.inkflow.crm.module.client.service.ClientService;
import com.inkflow.crm.module.project.dto.ProjectSummaryDto;
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
@RequestMapping("/clients")
@RequiredArgsConstructor
@Tag(name = "CRM · Clients")
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @RequirePermission({Permission.CLIENTS_VIEW_ALL, Permission.CLIENTS_VIEW_OWN})
    public ResponseEntity<ApiResponse<List<ClientDto>>> getAllClients(
            @ModelAttribute PageRequest pageRequest,
            @ModelAttribute ClientFilterRequest filter) {
        PageResult<ClientDto> result = clientService.getAllClients(pageRequest, filter);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/recent")
    @RequirePermission({Permission.CLIENTS_VIEW_ALL, Permission.CLIENTS_VIEW_OWN})
    public ResponseEntity<ApiResponse<List<ClientDto>>> getRecentClients() {
        return ResponseEntity.ok(ApiResponse.success(clientService.getRecentClients()));
    }

    @GetMapping("/{id}")
    @RequirePermission({Permission.CLIENTS_VIEW_ALL, Permission.CLIENTS_VIEW_OWN})
    public ResponseEntity<ApiResponse<ClientDetailDto>> getClient(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(clientService.getClientById(id)));
    }

    @PostMapping
    @RequirePermission(Permission.CLIENTS_CREATE)
    public ResponseEntity<ApiResponse<ClientDto>> createClient(@Valid @RequestBody CreateClientRequest request) {
        ClientDto client = clientService.createClient(request);
        log.info("Client created via API: clientId={}", client.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(client));
    }

    @PatchMapping("/{id}")
    @RequirePermission(Permission.CLIENTS_EDIT)
    public ResponseEntity<ApiResponse<ClientDto>> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        ClientDto client = clientService.updateClient(id, request);
        log.info("Client updated via API: clientId={}", id);

        return ResponseEntity.ok(ApiResponse.success(client));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(Permission.CLIENTS_DELETE)
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        clientService.deleteClient(id);
        log.info("Client deleted via API: clientId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/{id}/projects")
    public ResponseEntity<ApiResponse<List<ProjectSummaryDto>>> getClientProjects(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(clientService.getClientProjects(id)));
    }

    @GetMapping("/{id}/projects/active")
    public ResponseEntity<ApiResponse<List<ProjectSummaryDto>>> getClientActiveProjects(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(clientService.getClientActiveProjects(id)));
    }
}
