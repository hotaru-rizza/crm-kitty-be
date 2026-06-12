package com.inkflow.crm.module.email.dto;

public record EmailRecipient(String email, String name) {

    public static EmailRecipient of(String email, String name) {
        return new EmailRecipient(email, name);
    }
}
