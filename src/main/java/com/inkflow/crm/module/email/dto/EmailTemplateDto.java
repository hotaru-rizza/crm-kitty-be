package com.inkflow.crm.module.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplateDto {
    private String type;
    private String subject;
    private String body;
    private List<String> fields;
}
