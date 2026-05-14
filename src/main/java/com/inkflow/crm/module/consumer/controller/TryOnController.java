package com.inkflow.crm.module.consumer.controller;

import com.inkflow.crm.module.consumer.dto.PlacementDto;
import com.inkflow.crm.module.consumer.dto.TryOnRequest;
import com.inkflow.crm.module.consumer.dto.TryOnResponse;
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
    public ResponseEntity<TryOnResponse> tryOn(@RequestBody TryOnRequest request) {
        try {
            PlacementDto p = request.placement();
            log.info("Try-on request: placement xNorm={} yNorm={} sizeNorm={} angle={}",
                    p.xNorm(), p.yNorm(), p.sizeNorm(), p.angle());

            String resultDataUri = geminiService.generateTattooTryOn(
                    request.bodyImage(),
                    request.sketchImage(),
                    p.xNorm(), p.yNorm(), p.sizeNorm(), p.angle()
            );

            log.info("Try-on succeeded");
            return ResponseEntity.ok(TryOnResponse.success(resultDataUri));

        } catch (Exception e) {
            log.error("Try-on generation failed", e);
            return ResponseEntity.internalServerError()
                    .body(TryOnResponse.failure(e.getMessage()));
        }
    }
}
