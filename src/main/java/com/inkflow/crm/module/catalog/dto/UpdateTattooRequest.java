package com.inkflow.crm.module.catalog.dto;

import java.util.List;

public record UpdateTattooRequest(String description, List<String> tags) {}
