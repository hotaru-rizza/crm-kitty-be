package com.inkflow.crm.module.request.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.client.dto.ClientDto;
import com.inkflow.crm.module.request.dto.*;
import com.inkflow.crm.module.request.service.RequestMessageService;
import com.inkflow.crm.module.request.service.RequestService;
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
@RequestMapping("/requests")
@RequiredArgsConstructor
@Tag(name = "CRM · Requests")
public class RequestController {

    private final RequestService requestService;
    private final RequestMessageService requestMessageService;

    @GetMapping
    @RequirePermission(Permission.REQUESTS_VIEW)
    public ResponseEntity<ApiResponse<List<RequestDto>>> getAllRequests(
            @ModelAttribute PageRequest pageRequest,
            @ModelAttribute RequestFilterRequest filter) {
        PageResult<RequestDto> result = requestService.getAllRequests(pageRequest, filter);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    @RequirePermission(Permission.REQUESTS_VIEW)
    public ResponseEntity<ApiResponse<RequestDto>> getRequest(@PathVariable UUID id) {
        RequestDto request = requestService.getRequestById(id);
        return ResponseEntity.ok(ApiResponse.success(request));
    }

    @PostMapping
    @RequirePermission(Permission.REQUESTS_CREATE)
    public ResponseEntity<ApiResponse<RequestDto>> createRequest(@Valid @RequestBody CreateRequestRequest request) {
        RequestDto created = requestService.createRequest(request);
        log.info("Request created via API: requestId={}", created.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission(Permission.REQUESTS_CHANGE_STATUS)
    public ResponseEntity<ApiResponse<RequestDto>> updateRequestStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequestStatusRequest request) {
        RequestDto updated = requestService.updateRequestStatus(id, request);
        log.info("Request status updated via API: requestId={} status={}", id, request.getStatus());

        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PatchMapping("/{id}/assignment")
    @RequirePermission(Permission.REQUESTS_CHANGE_STATUS)
    public ResponseEntity<ApiResponse<RequestDto>> updateRequestAssignment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRequestAssignmentRequest request) {
        RequestDto updated = requestService.updateAssignment(id, request);
        log.info("Request assignment updated via API: requestId={} assignedStaffId={}",
                id, request.getAssignedStaffId());

        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/{id}/convert")
    @RequirePermission(value = {Permission.REQUESTS_CHANGE_STATUS, Permission.CLIENTS_CREATE}, requireAll = true)
    public ResponseEntity<ApiResponse<ClientDto>> convertToClient(
            @PathVariable UUID id,
            @Valid @RequestBody ConvertRequestRequest request) {
        ClientDto client = requestService.convertToClient(id, request);
        log.info("Request converted to client via API: requestId={} clientId={}", id, client.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(client));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(Permission.REQUESTS_CHANGE_STATUS)
    public ResponseEntity<ApiResponse<Void>> deleteRequest(@PathVariable UUID id) {
        requestService.deleteRequest(id);
        log.info("Request deleted via API: requestId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/{id}/messages")
    @RequirePermission(Permission.REQUESTS_VIEW)
    public ResponseEntity<ApiResponse<List<RequestMessageDto>>> getRequestMessages(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(requestMessageService.getMessagesForStaff(id)));
    }

    @PostMapping("/{id}/messages")
    @RequirePermission(Permission.REQUESTS_CHANGE_STATUS)
    public ResponseEntity<ApiResponse<RequestMessageDto>> sendRequestMessage(
            @PathVariable UUID id,
            @Valid @RequestBody CreateRequestMessageRequest request) {
        RequestMessageDto message = requestMessageService.sendStaffMessage(id, request);
        log.info("Request message sent via API: requestId={} messageId={}", id, message.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(message));
    }
}
