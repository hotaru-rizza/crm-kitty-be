package com.inkflow.crm.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "scheduler_run")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerRun {

    @Id
    @Column(name = "job_key", length = 64)
    private String jobKey;

    @Column(name = "last_run_at", nullable = false)
    private Instant lastRunAt;
}
