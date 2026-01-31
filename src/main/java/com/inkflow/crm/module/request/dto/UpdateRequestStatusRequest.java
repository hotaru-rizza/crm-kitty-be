package com.inkflow.crm.module.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRequestStatusRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(new|replied|converted|spam)$", message = "Invalid status")
    private String status;
}
