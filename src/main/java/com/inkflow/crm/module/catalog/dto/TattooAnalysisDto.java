package com.inkflow.crm.module.catalog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TattooAnalysisDto(String description, String altDescription, List<String> tags) {}
