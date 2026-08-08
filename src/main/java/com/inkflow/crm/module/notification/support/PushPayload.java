package com.inkflow.crm.module.notification.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PushPayload {

    public static final String SCHEMA_VERSION = "1";

    public static final String TYPE_NEW_REQUEST = "new_request";
    public static final String TYPE_REQUEST_MESSAGE = "request_message";
    public static final String TYPE_APPOINTMENT_REMINDER = "appointment_reminder";

    public static final String ENTITY_REQUEST = "request";
    public static final String ENTITY_APPOINTMENT = "appointment";

    private PushPayload() {
    }

    public static Map<String, String> forRequest(String type, UUID requestId, UUID tenantId) {
        return base(type, ENTITY_REQUEST, requestId, tenantId, "/requests/" + requestId);
    }

    public static Map<String, String> forAppointment(String type, UUID appointmentId, UUID tenantId) {
        return base(type, ENTITY_APPOINTMENT, appointmentId, tenantId, "/calendar/" + appointmentId);
    }

    private static Map<String, String> base(
            String type,
            String entityType,
            UUID entityId,
            UUID tenantId,
            String route) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("schemaVersion", SCHEMA_VERSION);
        data.put("type", type);
        data.put("entityType", entityType);
        data.put("entityId", entityId.toString());
        data.put("route", route);
        data.put("tenantId", tenantId.toString());
        return data;
    }
}
