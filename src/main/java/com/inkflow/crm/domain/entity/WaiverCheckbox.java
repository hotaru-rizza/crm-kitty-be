package com.inkflow.crm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiverCheckbox {

    @Column(name = "checkbox_id", nullable = false)
    private String checkboxId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = true;
}
