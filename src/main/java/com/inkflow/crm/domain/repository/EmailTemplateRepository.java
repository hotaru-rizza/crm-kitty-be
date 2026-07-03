package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.module.email.enums.TriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    List<EmailTemplate> findAllByOrderByCategoryAscTriggerTypeAscBuiltinKeyAsc();

    List<EmailTemplate> findByTriggerTypeAndEnabledTrue(TriggerType triggerType);

    Optional<EmailTemplate> findByBuiltinKey(String builtinKey);

    boolean existsByBuiltinKey(String builtinKey);
}
