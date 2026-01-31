package com.inkflow.crm.common.exception;

public class AccessDeniedException extends ApiException {

    public AccessDeniedException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }

    public static AccessDeniedException insufficientPermissions() {
        return new AccessDeniedException("You don't have permission to perform this action");
    }

    public static AccessDeniedException cannotModifyOthers() {
        return new AccessDeniedException("You can only modify your own records");
    }

    public static AccessDeniedException locationAccessDenied() {
        return new AccessDeniedException("You don't have access to this location");
    }
}
