package com.inkflow.crm.module.leave.dto;

import java.util.Map;

public record LeaveQueryParts(String dataJpql, String countJpql, Map<String, Object> params) {
}
