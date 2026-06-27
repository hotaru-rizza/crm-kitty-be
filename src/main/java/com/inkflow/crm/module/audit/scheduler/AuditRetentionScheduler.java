package com.inkflow.crm.module.audit.scheduler;

import com.inkflow.crm.common.scheduler.SchedulerRunService;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionScheduler {

    static final String JOB_KEY = "AUDIT_RETENTION";

    private final AuditLogRepository auditLogRepository;
    private final SchedulerRunService schedulerRunService;
    private final InkflowProperties properties;

    @Scheduled(cron = "${inkflow.audit.retention-cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredEntries() {
        InkflowProperties.Audit audit = properties.getAudit();
        if (!audit.isRetentionEnabled()) {
            return;
        }

        Instant cutoff = Instant.now().minus(audit.getRetentionDays(), ChronoUnit.DAYS);
        int deleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);

        schedulerRunService.markRun(JOB_KEY, Instant.now());
        log.info("Audit retention purge completed: deleted={} cutoff={}", deleted, cutoff);
    }
}
