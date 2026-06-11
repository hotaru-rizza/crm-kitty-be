package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import com.inkflow.crm.module.consumer.dto.GenerateResponse;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.service.AIGeneratorService;
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

    @PostMapping
    public ResponseEntity<ApiResponse<GenerateResponse>> generate(
            @AuthenticationPrincipal ConsumerUser consumer,
            @Valid @RequestBody GenerateRequest request) {
        ApiResponses.requireConsumer(consumer);

        GenerateResponse response = aiGeneratorService.generate(request);
        log.info("AI tattoo generation requested via API: consumerId={} style={}", consumer.getId(), request.style());

        return ApiResponses.ok(response);
    }
}
