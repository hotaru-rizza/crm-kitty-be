package com.inkflow.crm.module.email.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailModuleBootstrap {

    private final BuiltInTemplateSeeder builtInTemplateSeeder;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        builtInTemplateSeeder.seedAllTenants();
        log.info("Email module bootstrap completed");
    }
}
