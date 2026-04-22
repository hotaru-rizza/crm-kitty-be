package com.inkflow.crm.module.client.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.client.dto.*;
import com.inkflow.crm.module.client.service.ClientService;
import com.inkflow.crm.module.project.dto.ProjectSummaryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.inkflow.crm.security.RequirePermission;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    @RequirePermission({"clients.view_all", "clients.view_own"})
    public ResponseEntity<ApiResponse<List<ClientDto>>> getAllClients(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean onlyMine,
            @RequestParam(required = false) Boolean lost) {
        PageResult<ClientDto> result = clientService.getAllClients(pageRequest, search, status, onlyMine, lost);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    @RequirePermission({"clients.view_all", "clients.view_own"})
    public ResponseEntity<ApiResponse<ClientDetailDto>> getClient(@PathVariable UUID id) {
        ClientDetailDto client = clientService.getClientById(id);
        return ResponseEntity.ok(ApiResponse.success(client));
    }

    @PostMapping
    @RequirePermission("clients.create")
    public ResponseEntity<ApiResponse<ClientDto>> createClient(@Valid @RequestBody CreateClientRequest request) {
        ClientDto client = clientService.createClient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(client));
    }

    @PatchMapping("/{id}")
    @RequirePermission("clients.edit")
    public ResponseEntity<ApiResponse<ClientDto>> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        ClientDto client = clientService.updateClient(id, request);
        return ResponseEntity.ok(ApiResponse.success(client));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("clients.delete")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        clientService.deleteClient(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/{id}/projects")
    public ResponseEntity<ApiResponse<List<ProjectSummaryDto>>> getClientProjects(@PathVariable UUID id) {
        List<ProjectSummaryDto> projects = clientService.getClientProjects(id);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/{id}/projects/active")
    public ResponseEntity<ApiResponse<List<ProjectSummaryDto>>> getClientActiveProjects(@PathVariable UUID id) {
        List<ProjectSummaryDto> projects = clientService.getClientActiveProjects(id);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }
}
