package com.inkflow.crm.common.exception;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static BusinessRuleException phoneAlreadyExists(String phone) {
        return new BusinessRuleException(ErrorCode.PHONE_ALREADY_EXISTS, "Phone number already exists: " + phone);
    }

    public static BusinessRuleException emailAlreadyExists(String email) {
        return new BusinessRuleException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already exists: " + email);
    }

    public static BusinessRuleException timeSlotConflict() {
        return new BusinessRuleException(ErrorCode.TIME_SLOT_CONFLICT, "The selected time slot is already booked");
    }

    public static BusinessRuleException invalidStatusTransition(String from, String to) {
        return new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION, 
                String.format("Cannot transition from '%s' to '%s'", from, to));
    }
}
