package com.inkflow.crm.module.waiver.dto;

import com.inkflow.crm.domain.entity.ConsentField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicConsentDto {

    private String templateTitle;
    private List<ConsentFieldDto> fields;
    private String clientName;
    private Instant appointmentTime;
    private String studioName;
    private boolean alreadySigned;
}
