package com.inkflow.crm.common.scheduler;

import com.inkflow.crm.domain.entity.SchedulerRun;
import com.inkflow.crm.domain.repository.SchedulerRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Persists the wall-clock timestamp of the last successful run of a scheduled job,
 * so jobs can rebuild their processing window after downtime instead of silently
 * skipping everything that fell due while the application was offline.
 */
@Service
@RequiredArgsConstructor
public class SchedulerRunService {

    private final SchedulerRunRepository schedulerRunRepository;

    @Transactional(readOnly = true)
    public Optional<Instant> lastRun(String jobKey) {
        return schedulerRunRepository.findById(jobKey).map(SchedulerRun::getLastRunAt);
    }

    @Transactional
    public void markRun(String jobKey, Instant runAt) {
        SchedulerRun run = schedulerRunRepository.findById(jobKey)
                .orElseGet(() -> SchedulerRun.builder().jobKey(jobKey).build());
        run.setLastRunAt(runAt);
        schedulerRunRepository.save(run);
    }
}
