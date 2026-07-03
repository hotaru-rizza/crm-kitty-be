package com.inkflow.crm.module.finance.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.TransactionCategoryConfig;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.finance.dto.CategoryConfigDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryConfigService {

    private final TransactionCategoryConfigRepository repo;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

    private static final List<DefaultCategory> SYSTEM_DEFAULTS = List.of(
        new DefaultCategory("service",  "Послуга",             "#6366f1", "INCOME"),
        new DefaultCategory("tip",      "Чайові",              "#14b8a6", "INCOME"),
        new DefaultCategory("rent",     "Оренда",              "#f59e0b", "EXPENSE"),
        new DefaultCategory("supplies", "Витратні матеріали",  "#fb923c", "EXPENSE"),
        new DefaultCategory("salary",   "Зарплата",            "#ec4899", "EXPENSE"),
        new DefaultCategory("other",    "Інше",                "#94a3b8", "NEUTRAL")
    );

    private static final List<DefaultCategory> OPTIONAL_DEFAULTS = List.of(
        new DefaultCategory("marketing", "Маркетинг",   "#a855f7", "EXPENSE"),
        new DefaultCategory("equipment", "Обладнання",  "#06b6d4", "EXPENSE")
    );

    private record DefaultCategory(String key, String label, String color, String plType) {}

    @Transactional(readOnly = true)
    public List<CategoryConfigDto> getAll() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ensureDefaults(tenantId);
        return repo.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public CategoryConfigDto upsert(String categoryKey, String label, String color, String plType) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ensureDefaults(tenantId);

        Optional<TransactionCategoryConfig> existing =
                repo.findByCategoryKeyAndDeletedAtIsNull( categoryKey);

        boolean isNew = existing.isEmpty();
        TransactionCategoryConfig cfg = existing.orElseGet(() ->
                TransactionCategoryConfig.builder()
                        .tenantId(tenantId)
                        .categoryKey(categoryKey)
                        .isDefault(false)
                        .build()
        );
        cfg.setLabel(label);
        cfg.setColor(color);
        cfg.setPlType(plType);
        cfg.setIsActive(true);
        TransactionCategoryConfig saved = repo.save(cfg);
        auditRecorder.record(
                isNew ? AuditAction.CREATE : AuditAction.UPDATE,
                AuditEntityType.TRANSACTION,
                saved.getId().toString(),
                auditLabelFormatter.financeCategory(saved.getLabel())
        );
        return toDto(saved);
    }

    @Transactional
    public CategoryConfigDto create(String label, String color, String plType) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String key = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        TransactionCategoryConfig cfg = TransactionCategoryConfig.builder()
                .tenantId(tenantId)
                .categoryKey(key)
                .label(label)
                .color(color)
                .plType(plType)
                .isDefault(false)
                .isActive(true)
                .build();
        TransactionCategoryConfig saved = repo.save(cfg);
        auditRecorder.record(
                AuditAction.CREATE,
                AuditEntityType.TRANSACTION,
                saved.getId().toString(),
                auditLabelFormatter.financeCategory(saved.getLabel())
        );
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        repo.findById(id).ifPresent(cfg -> {
            if (cfg.getTenantId().equals(tenantId) && !Boolean.TRUE.equals(cfg.getIsDefault())) {
                String label = auditLabelFormatter.financeCategory(cfg.getLabel());
                cfg.setDeletedAt(java.time.Instant.now());
                repo.save(cfg);
                auditRecorder.record(
                        AuditAction.DELETE,
                        AuditEntityType.TRANSACTION,
                        cfg.getId().toString(),
                        label
                );
            }
        });
    }

    @Transactional(readOnly = true)
    public TransactionCategoryConfig requireActiveCategoryForTransaction(
            UUID tenantId,
            String categoryKey,
            TransactionType transactionType
    ) {
        ensureDefaults(tenantId);

        TransactionCategoryConfig config = repo
                .findByCategoryKeyAndDeletedAtIsNull( categoryKey)
                .orElseThrow(() -> new BusinessRuleException("Invalid transaction category"));

        if (!Boolean.TRUE.equals(config.getIsActive())) {
            throw new BusinessRuleException("Transaction category is inactive");
        }

        if (!matchesTransactionType(config.getPlType(), transactionType)) {
            throw new BusinessRuleException("Transaction category does not match transaction type");
        }

        return config;
    }

    private boolean matchesTransactionType(String plType, TransactionType transactionType) {
        if ("NEUTRAL".equals(plType)) {
            return true;
        }
        if (transactionType == TransactionType.INCOME && "INCOME".equals(plType)) {
            return true;
        }
        return transactionType == TransactionType.EXPENSE && "EXPENSE".equals(plType);
    }

    @Transactional
    public void ensureDefaults(UUID tenantId) {
        if (repo.existsByDeletedAtIsNull()) return;
        SYSTEM_DEFAULTS.forEach(d -> repo.save(buildConfig(tenantId, d, true)));
        OPTIONAL_DEFAULTS.forEach(d -> repo.save(buildConfig(tenantId, d, false)));
    }

    private TransactionCategoryConfig buildConfig(UUID tenantId, DefaultCategory d, boolean isDefault) {
        return TransactionCategoryConfig.builder()
                .tenantId(tenantId)
                .categoryKey(d.key())
                .label(d.label())
                .color(d.color())
                .plType(d.plType())
                .isDefault(isDefault)
                .isActive(true)
                .build();
    }

    private CategoryConfigDto toDto(TransactionCategoryConfig c) {
        return CategoryConfigDto.builder()
                .id(c.getId())
                .categoryKey(c.getCategoryKey())
                .label(c.getLabel())
                .color(c.getColor())
                .plType(c.getPlType())
                .isActive(c.getIsActive())
                .isDefault(c.getIsDefault())
                .build();
    }
}
