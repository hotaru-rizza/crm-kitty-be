package com.inkflow.crm.module.audit.annotation;

import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative audit for service mutations. Evaluated after successful method return.
 * SpEL expressions may reference method parameter names, {@code #result}, and Spring beans ({@code @beanName.method()}).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    AuditAction action();

    AuditEntityType entityType();

    /** SpEL → entity id string. Example: {@code #result.id} or {@code #id.toString()}. */
    String entityId();

    /** SpEL → human-readable label. Example: {@code @auditLabelFormatter.staff(#staff)}. */
    String entityLabel() default "";

    /** SpEL → UUID client id when the action involves a client. */
    String subjectClientId() default "";

    /** SpEL → free-text details (status transitions, error context, etc.). */
    String details() default "";
}
