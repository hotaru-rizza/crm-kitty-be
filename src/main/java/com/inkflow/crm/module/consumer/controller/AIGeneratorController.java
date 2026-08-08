package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.consumer.config.ConsumerAiProperties;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import com.inkflow.crm.module.consumer.dto.GenerateResponse;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.service.AIGeneratorService;
import com.inkflow.crm.module.consumer.service.ConsumerTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/public/consumer/generate")
@RequiredArgsConstructor
@Tag(name = "Consumer · AI")
public class AIGeneratorController {

    private final AIGeneratorService aiGeneratorService;
    private final ConsumerTokenService consumerTokenService;
    private final ConsumerAiProperties consumerAiProperties;

    @PostMapping
    public ResponseEntity<ApiResponse<GenerateResponse>> generate(
            @AuthenticationPrincipal ConsumerUser consumer,
            @Valid @RequestBody GenerateRequest request) {
        ApiResponses.requireConsumer(consumer);

        int cost = consumerAiProperties.getCost().getGeneration();
        consumerTokenService.assertCanAfford(consumer, cost);

        GenerateResponse response = aiGeneratorService.generate(request);

        if (response.error() == null && response.images() != null && !response.images().isEmpty()) {
            int remaining = consumerTokenService.chargeAndGetRemaining(consumer.getId(), cost);
            response = GenerateResponse.success(response.images(), remaining);
        }

        log.info("AI tattoo generation requested via API: consumerId={} style={} charged={}",
                consumer.getId(), request.style(), response.remainingTokens() != null);

        return ApiResponses.ok(response);
    }
}
