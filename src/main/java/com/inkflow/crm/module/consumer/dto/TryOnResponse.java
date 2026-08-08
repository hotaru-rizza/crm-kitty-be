package com.inkflow.crm.module.consumer.dto;

public record TryOnResponse(String resultUrl, String error, Integer remainingTokens) {

    public static TryOnResponse success(String url, int remainingTokens) {
        return new TryOnResponse(url, null, remainingTokens);
    }

    public static TryOnResponse failure(String message) {
        return new TryOnResponse(null, message, null);
    }
}
