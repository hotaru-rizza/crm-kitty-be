package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingListItemDto;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingResultDto;
import com.inkflow.crm.module.consumer.dto.PublicBookingRequest;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.service.ConsumerBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/public/consumer/requests")
@RequiredArgsConstructor
public class ConsumerBookingController {

    private final ConsumerBookingService consumerBookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConsumerBookingResultDto>> submitBookingRequest(
            @AuthenticationPrincipal ConsumerUser consumer,
            @Valid @RequestBody PublicBookingRequest body) {
        ConsumerBookingResultDto result = consumerBookingService.submitBookingRequest(consumer, body);
        log.info("Consumer booking request submitted via API: consumerId={} requestId={}", consumer.getId(), result.id());

        return ApiResponses.created(result);
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ConsumerBookingListItemDto>>> getMyRequests(
            @AuthenticationPrincipal ConsumerUser consumer) {
        return ApiResponses.ok(consumerBookingService.getMyRequests(consumer));
    }
}
