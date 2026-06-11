package com.inkflow.crm.module.catalog.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingServiceTest {

    private final EmbeddingService embeddingService = new EmbeddingService();

    @Test
    void toPgVector_formatsFloatArray() {
        assertEquals("[1.0,2.0,3.0]", embeddingService.toPgVector(new float[]{1f, 2f, 3f}));
    }

    @Test
    void toPgVector_handlesSingleValue() {
        assertEquals("[0.5]", embeddingService.toPgVector(new float[]{0.5f}));
    }
}
