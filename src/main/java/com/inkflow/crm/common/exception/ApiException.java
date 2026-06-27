package com.inkflow.crm.common.exception;

import com.inkflow.crm.module.audit.dto.AuditContext;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String details;
    private final AuditContext auditContext;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null, null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public ApiException(ErrorCode errorCode, String message, String details) {
        this(errorCode, message, details, null);
    }

    public ApiException(ErrorCode errorCode, String message, AuditContext auditContext) {
        this(errorCode, message, null, auditContext);
    }

    public ApiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
        this.auditContext = null;
    }

    private ApiException(ErrorCode errorCode, String message, String details, AuditContext auditContext) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
        this.auditContext = auditContext;
    }
}
