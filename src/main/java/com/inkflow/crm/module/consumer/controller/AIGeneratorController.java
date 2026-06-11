package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import com.inkflow.crm.module.consumer.dto.GenerateResponse;
import com.inkflow.crm.module.consumer.service.AIGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/public/consumer/generate")
@RequiredArgsConstructor
public class AIGeneratorController {

    private final AIGeneratorService aiGeneratorService;

    @PostMapping
    public ResponseEntity<ApiResponse<GenerateResponse>> generate(@RequestBody GenerateRequest request) {
        GenerateResponse response = aiGeneratorService.generate(request);
        log.info("AI tattoo generation requested via API: style={}", request.style());

        return ApiResponses.ok(response);
    }
}
