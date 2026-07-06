package com.inkflow.crm.module.staff.scheduler;

import com.inkflow.crm.common.scheduler.SchedulerRunService;
import com.inkflow.crm.config.BypassTenantFilter;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.repository.StaffInviteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@BypassTenantFilter
@RequiredArgsConstructor
public class StaffInviteCleanupScheduler {

    static final String JOB_KEY = "STAFF_INVITE_CLEANUP";

    private final StaffInviteRepository staffInviteRepository;
    private final SchedulerRunService schedulerRunService;
    private final InkflowProperties properties;

    @Scheduled(cron = "${inkflow.invite.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void purgeExpiredInvites() {
        InkflowProperties.Invite invite = properties.getInvite();
        if (!invite.isCleanupEnabled()) {
            return;
        }

        int retentionDays = Math.max(0, invite.getCleanupRetentionDaysAfterExpiry());
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = staffInviteRepository.deleteByAcceptedAtIsNullAndExpiresAtBefore(cutoff);

        schedulerRunService.markRun(JOB_KEY, Instant.now());
        log.info("Staff invite cleanup completed: deleted={} cutoff={}", deleted, cutoff);
    }
}
