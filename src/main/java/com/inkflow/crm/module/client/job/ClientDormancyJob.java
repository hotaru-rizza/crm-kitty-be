package com.inkflow.crm.module.client.job;

import com.inkflow.crm.config.BypassTenantFilter;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.client.service.ClientDormancyService;
import com.inkflow.crm.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@BypassTenantFilter
@RequiredArgsConstructor
@Slf4j
public class ClientDormancyJob {

    private final TenantRepository tenantRepository;
    private final ClientDormancyService clientDormancyService;

    @Scheduled(cron = "0 0 3 * * *")
    public void processDormantClients() {
        tenantRepository.findAll().forEach(tenant -> {
            int inactivityDays = tenant.getClientDormancyDays() != null ? tenant.getClientDormancyDays() : 90;
            Instant cutoff = Instant.now().minus(inactivityDays, ChronoUnit.DAYS);

            TenantContext.setCurrentTenant(tenant.getId());
            try {
                ClientDormancyService.DormancyResult result = clientDormancyService.processDormancy(cutoff);
                if (result.markedDormant() > 0 || result.reactivated() > 0) {
                    log.info("Client dormancy processed: tenantId={} markedDormant={} reactivated={}",
                            tenant.getId(), result.markedDormant(), result.reactivated());
                }
            } finally {
                TenantContext.clear();
            }
        });
    }
}
