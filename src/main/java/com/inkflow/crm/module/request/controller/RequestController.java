package com.inkflow.crm.module.request.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.client.dto.ClientDto;
import com.inkflow.crm.module.request.dto.*;
import com.inkflow.crm.module.request.service.RequestService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @GetMapping
    @RequirePermission("requests.view")
    public ResponseEntity<ApiResponse<List<RequestDto>>> getAllRequests(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) List<String> source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID locationId) {
        PageResult<RequestDto> result = requestService.getAllRequests(pageRequest, status, source, from, to, locationId);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    @RequirePermission("requests.view")
    public ResponseEntity<ApiResponse<RequestDto>> getRequest(@PathVariable UUID id) {
        RequestDto request = requestService.getRequestById(id);
        return ResponseEntity.ok(ApiResponse.success(request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RequestDto>> createRequest(@Valid @RequestBody CreateRequestRequest request) {
        RequestDto created = requestService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("requests.change_status")
    public ResponseEntity<ApiResponse<RequestDto>> updateRequestStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequestStatusRequest request) {
        RequestDto updated = requestService.updateRequestStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/{id}/convert")
    @RequirePermission(value = {"requests.change_status", "clients.create"}, requireAll = true)
    public ResponseEntity<ApiResponse<ClientDto>> convertToClient(
            @PathVariable UUID id,
            @Valid @RequestBody ConvertRequestRequest request) {
        ClientDto client = requestService.convertToClient(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(client));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("requests.change_status")
    public ResponseEntity<ApiResponse<Void>> deleteRequest(@PathVariable UUID id) {
        requestService.deleteRequest(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
