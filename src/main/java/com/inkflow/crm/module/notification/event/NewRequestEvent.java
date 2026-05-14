package com.inkflow.crm.module.notification.event;

import java.util.UUID;

public record NewRequestEvent(
        UUID requestId,
        UUID tenantId,
        UUID staffId,
        String clientName,
        String idea
) {}
