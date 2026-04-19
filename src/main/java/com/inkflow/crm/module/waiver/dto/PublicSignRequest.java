package com.inkflow.crm.module.waiver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicSignRequest {

    @NotBlank(message = "Signature data is required")
    private String signatureData;

    private Map<String, Object> fieldValues;
}
