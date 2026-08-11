package com.inkflow.crm.module.notification.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.notification.dto.StaffNotificationDto;
import com.inkflow.crm.module.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StaffNotificationMapper {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public StaffNotificationDto toDto(Notification notification) {
        return new StaffNotificationDto(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                parseData(notification.getData()),
                Boolean.TRUE.equals(notification.getIsRead()),
                Boolean.TRUE.equals(notification.getIsSent()),
                notification.getCreatedAt()
        );
    }

    private Map<String, String> parseData(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(raw, STRING_MAP);
        } catch (Exception e) {
            log.debug("Failed to parse notification data JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
