package com.inkflow.crm.module.consumer.repository;

import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConsumerUserRepository extends JpaRepository<ConsumerUser, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ConsumerUser u SET u.aiTokens = u.aiTokens - :cost WHERE u.id = :id AND u.aiTokens >= :cost")
    int spendTokens(@Param("id") UUID id, @Param("cost") int cost);

    @Query("SELECT u.aiTokens FROM ConsumerUser u WHERE u.id = :id")
    Optional<Integer> findAiTokensById(@Param("id") UUID id);
}
