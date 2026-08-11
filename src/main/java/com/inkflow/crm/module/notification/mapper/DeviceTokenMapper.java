package com.inkflow.crm.module.notification.mapper;

import com.inkflow.crm.module.notification.dto.DeviceTokenDto;
import com.inkflow.crm.module.notification.entity.DeviceToken;
import org.springframework.stereotype.Component;

@Component
public class DeviceTokenMapper {

    public DeviceTokenDto toDto(DeviceToken entity) {
        return new DeviceTokenDto(
                entity.getId(),
                entity.getPlatform(),
                entity.getAppVersion(),
                entity.getCreatedAt(),
                entity.getLastUsedAt()
        );
    }
}
