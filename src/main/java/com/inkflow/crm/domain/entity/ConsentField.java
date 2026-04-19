package com.inkflow.crm.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * A single field in a consent form template.
 * Stored as JSONB array inside waiver_templates.fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentField implements Serializable {

    private String id;
    private FieldType type;
    private String label;
    private String content;
    @Builder.Default
    private Boolean required = false;

    public enum FieldType {
        HEADING,
        PARAGRAPH,
        CHECKBOX,
        SIGNATURE,
        INITIALS,
        TEXT_INPUT,
        DATE
    }
}
