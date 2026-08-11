package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.consumer.dto.ConsumerAttachmentDto;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingListItemDto;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingResultDto;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingRequest;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.service.ConsumerBookingService;
import com.inkflow.crm.module.request.dto.CreateRequestMessageRequest;
import com.inkflow.crm.module.request.dto.RequestMessageDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/public/consumer/requests")
@RequiredArgsConstructor
@Tag(name = "Consumer · Booking")
public class ConsumerBookingController {

    private final ConsumerBookingService consumerBookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConsumerBookingResultDto>> submitBookingRequest(
            @AuthenticationPrincipal ConsumerUser consumer,
            @Valid @RequestBody ConsumerBookingRequest body) {
        ApiResponses.requireConsumer(consumer);

        ConsumerBookingResultDto result = consumerBookingService.submitBookingRequest(consumer, body);
        log.info("Consumer booking request submitted via API: consumerId={} requestId={}", consumer.getId(), result.id());

        return ApiResponses.created(result);
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ConsumerBookingListItemDto>>> getMyRequests(
            @AuthenticationPrincipal ConsumerUser consumer) {
        return ApiResponses.ok(consumerBookingService.getMyRequests(consumer));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<List<RequestMessageDto>>> getRequestMessages(
            @AuthenticationPrincipal ConsumerUser consumer,
            @PathVariable UUID id) {
        ApiResponses.requireConsumer(consumer);
        return ApiResponses.ok(consumerBookingService.getRequestMessages(consumer, id));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<RequestMessageDto>> sendRequestMessage(
            @AuthenticationPrincipal ConsumerUser consumer,
            @PathVariable UUID id,
            @Valid @RequestBody CreateRequestMessageRequest body) {
        ApiResponses.requireConsumer(consumer);
        RequestMessageDto message = consumerBookingService.sendRequestMessage(consumer, id, body);
        log.info("Consumer request message sent via API: consumerId={} requestId={} messageId={}",
                consumer.getId(), id, message.getId());

        return ApiResponses.created(message);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ConsumerAttachmentDto>> uploadRequestAttachment(
            @AuthenticationPrincipal ConsumerUser consumer,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        ApiResponses.requireConsumer(consumer);
        ConsumerAttachmentDto attachment = consumerBookingService.uploadRequestAttachment(consumer, id, file);
        log.info("Consumer request attachment uploaded via API: consumerId={} requestId={}", consumer.getId(), id);

        return ApiResponses.created(attachment);
    }
}
