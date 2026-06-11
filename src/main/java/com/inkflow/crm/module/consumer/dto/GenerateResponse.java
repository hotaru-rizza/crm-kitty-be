package com.inkflow.crm.module.consumer.dto;

import java.util.List;

public record GenerateResponse(List<String> images, String error) {

    public static GenerateResponse success(List<String> images) {
        return new GenerateResponse(images, null);
    }

    public static GenerateResponse failure(String message) {
        return new GenerateResponse(List.of(), message);
    }
}
