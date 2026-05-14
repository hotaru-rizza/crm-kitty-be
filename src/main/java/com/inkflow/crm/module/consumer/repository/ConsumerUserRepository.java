package com.inkflow.crm.module.consumer.repository;

import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsumerUserRepository extends JpaRepository<ConsumerUser, UUID> {
}
