package com.inkflow.crm.module.location.dto;

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
public class UpdateLocationRequest {

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    private String googleMapsLink;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code")
    private String color;

    private Boolean isActive;
    private String photoUrl;
}
