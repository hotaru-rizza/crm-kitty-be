package com.inkflow.crm.module.waiver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiverTemplateDto {
    private UUID id;
    private String title;
    private String content;
    private Integer version;
    private Boolean isActive;
    private List<ConsentFieldDto> fields;
    private Instant createdAt;

    // Legacy support
    private List<CheckboxDto> checkboxes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckboxDto {
        private String id;
        private String label;
        private Boolean isRequired;
    }
}
