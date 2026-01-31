package com.inkflow.crm.common.exception;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ResourceNotFoundException client(String id) {
        return new ResourceNotFoundException(ErrorCode.CLIENT_NOT_FOUND, "Client not found: " + id);
    }

    public static ResourceNotFoundException staff(String id) {
        return new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND, "Staff member not found: " + id);
    }

    public static ResourceNotFoundException service(String id) {
        return new ResourceNotFoundException(ErrorCode.SERVICE_NOT_FOUND, "Service not found: " + id);
    }

    public static ResourceNotFoundException appointment(String id) {
        return new ResourceNotFoundException(ErrorCode.APPOINTMENT_NOT_FOUND, "Appointment not found: " + id);
    }

    public static ResourceNotFoundException project(String id) {
        return new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, "Project not found: " + id);
    }

    public static ResourceNotFoundException location(String id) {
        return new ResourceNotFoundException(ErrorCode.LOCATION_NOT_FOUND, "Location not found: " + id);
    }

    public static ResourceNotFoundException request(String id) {
        return new ResourceNotFoundException(ErrorCode.REQUEST_NOT_FOUND, "Request not found: " + id);
    }

    public static ResourceNotFoundException transaction(String id) {
        return new ResourceNotFoundException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found: " + id);
    }
}
