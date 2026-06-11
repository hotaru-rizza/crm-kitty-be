package com.inkflow.crm.module.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddAppointmentPhotoRequest {
    @NotBlank
    private String url;

    @NotBlank
    private String stage;
}
