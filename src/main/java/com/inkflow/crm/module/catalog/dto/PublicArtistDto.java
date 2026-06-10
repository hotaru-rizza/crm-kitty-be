package com.inkflow.crm.module.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicArtistDto(
        UUID id,
        String name,
        String avatar,
        String bio,
        int experience,
        BigDecimal hourlyRate,
        String studioName,
        String studioAddress,
        String studioPhoto,
        Double lat,
        Double lng,
        List<String> styles,
        List<String> dontDoList,
        String instagramUrl,
        boolean isOpen,
        int savesCount,
        List<String> portfolio,
        List<ScheduleEntry> schedule,
        List<FaqEntry> faq,
        List<ReviewEntry> reviews
) {
    public record ScheduleEntry(String day, String hours) {}
    public record FaqEntry(String question, String answer) {}
    public record ReviewEntry(String id, String name, int rating, String text, String date) {}
}
