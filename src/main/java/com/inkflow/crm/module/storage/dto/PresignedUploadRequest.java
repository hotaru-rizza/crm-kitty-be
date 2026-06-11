package com.inkflow.crm.module.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PresignedUploadRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z_]+$", message = "Folder must contain only lowercase letters and underscores")
    private String folder;

    @NotBlank
    private String filename;

    @NotBlank
    private String contentType;
}
