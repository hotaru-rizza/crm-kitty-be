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

    public static BusinessRuleException emailRequired() {
        return new BusinessRuleException("Email is required");
    }

    public static BusinessRuleException timeSlotConflict() {
        return new BusinessRuleException(ErrorCode.TIME_SLOT_CONFLICT, "The selected time slot is already booked");
    }

    public static BusinessRuleException artistOnLeave() {
        return new BusinessRuleException(ErrorCode.ARTIST_ON_LEAVE, "Artist is on leave on the selected day");
    }

    public static BusinessRuleException invalidStatusTransition(String from, String to) {
        return new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION,
                String.format("Cannot transition from '%s' to '%s'", from, to));
    }

    public static BusinessRuleException projectDeleteRequiresArchive() {
        return new BusinessRuleException("Only archived projects can be deleted");
    }

    public static BusinessRuleException clientBlacklisted() {
        return new BusinessRuleException(ErrorCode.CLIENT_BLACKLISTED, "Client is blacklisted");
    }
}
