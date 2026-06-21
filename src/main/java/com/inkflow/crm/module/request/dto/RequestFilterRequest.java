package com.inkflow.crm.module.request.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class RequestFilterRequest {
    private String status;
    private List<String> source;
    private Instant from;
    private Instant to;
    private UUID locationId;

    private String search;
    private String city;
    private String tattooSize;
    private String tattooTiming;
    private Boolean isCoverUp;
    private Boolean hasSketch;
    private Boolean hasReferences;
    private String bodyZone;
    private UUID staffId;
}
