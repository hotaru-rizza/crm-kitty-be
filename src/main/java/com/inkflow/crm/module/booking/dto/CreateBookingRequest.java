package com.inkflow.crm.module.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {
    
    @NotNull(message = "Artist ID is required")
    private UUID artistId;
    
    @NotNull(message = "Service ID is required")
    private UUID serviceId;
    
    @NotNull(message = "Date is required")
    private LocalDate date;
    
    @NotNull(message = "Start time is required")
    private LocalTime startTime;
    
    // Client info
    @NotBlank(message = "Client name is required")
    private String clientName;
    
    @NotBlank(message = "Phone is required")
    private String phone;
    
    private String email;
    private String instagram;
    private String telegram;
    
    // Optional message/notes
    private String message;
    
    // Reference image URL (if client uploads a reference)
    private String referenceImageUrl;
}
