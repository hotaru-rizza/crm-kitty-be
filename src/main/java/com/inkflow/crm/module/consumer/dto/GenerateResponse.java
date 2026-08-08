package com.inkflow.crm.module.consumer.dto;

import java.util.List;

public record GenerateResponse(List<String> images, String error, Integer remainingTokens) {

    public static GenerateResponse success(List<String> images, int remainingTokens) {
        return new GenerateResponse(images, null, remainingTokens);
    }

    public static GenerateResponse failure(String message) {
        return new GenerateResponse(List.of(), message, null);
    }
}
