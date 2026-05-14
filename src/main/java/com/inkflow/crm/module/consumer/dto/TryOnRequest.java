package com.inkflow.crm.module.consumer.dto;

public record TryOnRequest(
        String bodyImage,
        String sketchImage,
        PlacementDto placement
) {}
