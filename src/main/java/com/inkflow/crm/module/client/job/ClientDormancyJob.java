package com.inkflow.crm.module.client.job;

import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClientDormancyJob {

    private final TenantRepository tenantRepository;
    private final ClientRepository clientRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void processDormantClients() {
        tenantRepository.findAll().forEach(tenant -> {
            int inactivityDays = tenant.getClientDormancyDays() != null ? tenant.getClientDormancyDays() : 90;
            Instant cutoff = Instant.now().minus(inactivityDays, ChronoUnit.DAYS);

            int markedDormant = clientRepository.markDormantClients(tenant.getId(), cutoff);
            int reactivated = clientRepository.reactivateDormantClients(tenant.getId(), cutoff);

            if (markedDormant > 0 || reactivated > 0) {
                log.info("Client dormancy processed: tenantId={} markedDormant={} reactivated={}",
                        tenant.getId(), markedDormant, reactivated);
            }
        });
    }
}
