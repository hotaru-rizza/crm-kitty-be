package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.SchedulerRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchedulerRunRepository extends JpaRepository<SchedulerRun, String> {
}
