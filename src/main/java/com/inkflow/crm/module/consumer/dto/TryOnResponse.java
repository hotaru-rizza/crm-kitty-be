package com.inkflow.crm.module.consumer.dto;

public record TryOnResponse(String resultUrl, String error) {

    public static TryOnResponse success(String url) {
        return new TryOnResponse(url, null);
    }

    public static TryOnResponse failure(String message) {
        return new TryOnResponse(null, message);
    }
}
