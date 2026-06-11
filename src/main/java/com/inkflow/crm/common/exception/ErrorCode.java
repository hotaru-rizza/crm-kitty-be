package com.inkflow.crm.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {


    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed", HttpStatus.BAD_REQUEST),
    NOT_FOUND("NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),


    UNAUTHORIZED("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN),
    INVALID_TOKEN("INVALID_TOKEN", "Invalid or expired token", HttpStatus.UNAUTHORIZED),


    CONFLICT("CONFLICT", "Resource conflict", HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION("BUSINESS_RULE_VIOLATION", "Business rule violation", HttpStatus.UNPROCESSABLE_ENTITY),


    CLIENT_NOT_FOUND("CLIENT_NOT_FOUND", "Client not found", HttpStatus.NOT_FOUND),
    STAFF_NOT_FOUND("STAFF_NOT_FOUND", "Staff member not found", HttpStatus.NOT_FOUND),
    SERVICE_NOT_FOUND("SERVICE_NOT_FOUND", "Service not found", HttpStatus.NOT_FOUND),
    APPOINTMENT_NOT_FOUND("APPOINTMENT_NOT_FOUND", "Appointment not found", HttpStatus.NOT_FOUND),
    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "Project not found", HttpStatus.NOT_FOUND),
    LOCATION_NOT_FOUND("LOCATION_NOT_FOUND", "Location not found", HttpStatus.NOT_FOUND),
    REQUEST_NOT_FOUND("REQUEST_NOT_FOUND", "Request not found", HttpStatus.NOT_FOUND),
    TRANSACTION_NOT_FOUND("TRANSACTION_NOT_FOUND", "Transaction not found", HttpStatus.NOT_FOUND),

    PHONE_ALREADY_EXISTS("PHONE_ALREADY_EXISTS", "Phone number already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "Email already exists", HttpStatus.CONFLICT),
    TIME_SLOT_CONFLICT("TIME_SLOT_CONFLICT", "Time slot is already booked", HttpStatus.CONFLICT),


    INVALID_STATUS_TRANSITION("INVALID_STATUS_TRANSITION", "Invalid status transition", HttpStatus.UNPROCESSABLE_ENTITY),


    STAFF_ALREADY_DEACTIVATED("STAFF_ALREADY_DEACTIVATED", "Staff member is already deactivated", HttpStatus.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
}
