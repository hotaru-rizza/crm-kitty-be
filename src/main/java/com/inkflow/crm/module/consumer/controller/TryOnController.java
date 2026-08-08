package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.consumer.config.ConsumerAiProperties;
import com.inkflow.crm.module.consumer.dto.TryOnRequest;
import com.inkflow.crm.module.consumer.dto.TryOnResponse;
import com.inkflow.crm.module.consumer.dto.PlacementDto;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.service.ConsumerTokenService;
import com.inkflow.crm.module.consumer.service.GeminiTattooService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/public/consumer/try-on")
@RequiredArgsConstructor
@Tag(name = "Consumer · AI")
public class TryOnController {

    private final GeminiTattooService geminiService;
    private final ConsumerTokenService consumerTokenService;
    private final ConsumerAiProperties consumerAiProperties;

    @PostMapping
    public ResponseEntity<ApiResponse<TryOnResponse>> tryOn(
            @AuthenticationPrincipal ConsumerUser consumer,
            @Valid @RequestBody TryOnRequest request) {
        ApiResponses.requireConsumer(consumer);

        int cost = consumerAiProperties.getCost().getTryOn();
        consumerTokenService.assertCanAfford(consumer, cost);

        try {
            PlacementDto placement = request.placement();

            String resultDataUri = geminiService.generateTattooTryOn(
                    request.bodyImage(),
                    request.sketchImage(),
                    placement.xNorm(), placement.yNorm(), placement.sizeNorm(), placement.angle()
            );

            int remaining = consumerTokenService.chargeAndGetRemaining(consumer.getId(), cost);

            log.info("Tattoo try-on generated via API: consumerId={} xNorm={} yNorm={} sizeNorm={} angle={} remainingTokens={}",
                    consumer.getId(), placement.xNorm(), placement.yNorm(), placement.sizeNorm(), placement.angle(), remaining);

            return ApiResponses.ok(TryOnResponse.success(resultDataUri, remaining));

        } catch (Exception e) {
            log.error("Tattoo try-on generation failed via API: consumerId={}", consumer.getId(), e);
            return ApiResponses.ok(TryOnResponse.failure(e.getMessage()));
        }
    }
}
