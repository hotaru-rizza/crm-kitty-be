package com.inkflow.crm.module.waiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTemplateRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotEmpty(message = "At least one field is required")
    private List<ConsentFieldDto> fields;
}
