package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.module.consumer.dto.TryOnRequest;
import com.inkflow.crm.module.consumer.dto.TryOnResponse;
import com.inkflow.crm.module.consumer.dto.PlacementDto;
import com.inkflow.crm.module.consumer.service.GeminiTattooService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/public/consumer/try-on")
@RequiredArgsConstructor
public class TryOnController {

    private final GeminiTattooService geminiService;

    @PostMapping
    public ResponseEntity<ApiResponse<TryOnResponse>> tryOn(@RequestBody TryOnRequest request) {
        try {
            PlacementDto placement = request.placement();

            String resultDataUri = geminiService.generateTattooTryOn(
                    request.bodyImage(),
                    request.sketchImage(),
                    placement.xNorm(), placement.yNorm(), placement.sizeNorm(), placement.angle()
            );

            log.info("Tattoo try-on generated via API: xNorm={} yNorm={} sizeNorm={} angle={}",
                    placement.xNorm(), placement.yNorm(), placement.sizeNorm(), placement.angle());

            return ApiResponses.ok(TryOnResponse.success(resultDataUri));

        } catch (Exception e) {
            log.error("Tattoo try-on generation failed via API", e);
            return ApiResponses.ok(TryOnResponse.failure(e.getMessage()));
        }
    }
}
