package com.inkflow.crm.module.staff.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertFaqRequest {

    private List<FaqItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaqItem {
        @NotBlank
        private String question;
        @NotBlank
        private String answer;
    }
}
