package com.inkflow.crm.module.staff.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStaffServicesRequest {

    @NotNull(message = "Services list is required")
    private List<ServiceAssignment> services;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceAssignment {
        @NotNull(message = "Service ID is required")
        private UUID serviceId;

        private BigDecimal customPrice;
        private Integer customDuration;
    }
}
