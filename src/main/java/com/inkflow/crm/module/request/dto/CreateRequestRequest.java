package com.inkflow.crm.module.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRequestRequest {

    @NotBlank(message = "Source is required")
    @Pattern(regexp = "^(instagram|telegram|website|referral|walk_in|other)$", message = "Invalid source")
    private String source;

    @NotBlank(message = "Client name is required")
    @Size(min = 1, max = 100, message = "Client name must be between 1 and 100 characters")
    private String clientName;

    @Size(max = 50, message = "Client nickname must not exceed 50 characters")
    private String clientNickname;

    private String message;
    private String phone;
    private String instagram;
}
