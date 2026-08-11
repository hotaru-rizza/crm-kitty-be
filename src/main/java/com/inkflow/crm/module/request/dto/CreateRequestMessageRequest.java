package com.inkflow.crm.module.request.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRequestMessageRequest {

    @Size(max = 2000)
    private String body;

    @Size(max = 2000)
    private String imageUrl;
}
