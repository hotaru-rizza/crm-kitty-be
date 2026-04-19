package com.inkflow.crm.module.waiver.dto;

import com.inkflow.crm.domain.entity.ConsentField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentFieldDto {

    private String id;
    private ConsentField.FieldType type;
    private String label;
    private String content;
    @Builder.Default
    private Boolean required = false;

    public static ConsentFieldDto fromEntity(ConsentField field) {
        return ConsentFieldDto.builder()
                .id(field.getId())
                .type(field.getType())
                .label(field.getLabel())
                .content(field.getContent())
                .required(field.getRequired())
                .build();
    }

    public ConsentField toEntity() {
        return ConsentField.builder()
                .id(id)
                .type(type)
                .label(label)
                .content(content)
                .required(required != null ? required : false)
                .build();
    }
}
